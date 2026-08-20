package com.mamoki.ieojuda.domain.plan.service;

import tools.jackson.databind.ObjectMapper;
import com.mamoki.ieojuda.domain.plan.entity.Conversation;
import com.mamoki.ieojuda.domain.plan.entity.LifeAreaMessage;
import com.mamoki.ieojuda.domain.plan.entity.MessageRole;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.ConversationRepository;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaMessageRepository;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.openai.component.OpenAIClient;
import com.mamoki.ieojuda.global.openai.dto.OpenAIMessageDto;
import com.mamoki.ieojuda.global.openai.dto.OpenAIResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// issue #91 완료 조건 - "자격증명 의심 입력이 저장되지 않는다" / "OpenAI로 전송되지 않는다"의
// 대화 메세지 저장 시점(1차) · OpenAI 전송 직전(2차) 방어선을 검증한다.
class ConversationServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    private PlanOwnershipReader planOwnershipReader;
    private ConversationRepository conversationRepository;
    private LifeAreaMessageRepository lifeAreaMessageRepository;
    private LifeAreaRepository lifeAreaRepository;
    private ItemRepository itemRepository;
    private OpenAIClient openAIClient;
    private ConversationService conversationService;

    private Plan plan;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        planOwnershipReader = mock(PlanOwnershipReader.class);
        conversationRepository = mock(ConversationRepository.class);
        lifeAreaMessageRepository = mock(LifeAreaMessageRepository.class);
        lifeAreaRepository = mock(LifeAreaRepository.class);
        itemRepository = mock(ItemRepository.class);
        openAIClient = mock(OpenAIClient.class);
        conversationService = new ConversationService(planOwnershipReader, conversationRepository,
                lifeAreaMessageRepository, lifeAreaRepository, itemRepository, openAIClient, new ObjectMapper());

        plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(PLAN_ID);
        conversation = mock(Conversation.class);
        when(conversation.getConversationId()).thenReturn(CONVERSATION_ID);
        when(conversation.getPlan()).thenReturn(plan);
        when(planOwnershipReader.findOwnedPlan(USER_ID, PLAN_ID)).thenReturn(plan);
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(lifeAreaMessageRepository.findByConversation_ConversationIdOrderByCreatedAtAscMessageIdAsc(CONVERSATION_ID))
                .thenReturn(List.of());
        when(lifeAreaMessageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void sendMessage_whenUserContentContainsCredentialValue_throwsWithoutSavingOrCallingOpenAI() {
        assertThatThrownBy(() -> conversationService.sendMessage(
                USER_ID, PLAN_ID, CONVERSATION_ID, "인스타그램 비밀번호는 abcd1234야"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SUSPECTED_CREDENTIAL_INPUT));

        verify(lifeAreaMessageRepository, never()).save(any());
        verify(openAIClient, never()).getChatCompletion(any());
    }

    // 1차 방어(저장 전 검증)가 생기기 전에 저장된 과거 이력에 자격증명이 남아있는 상황을 재현
    @Test
    void sendMessage_whenPastHistoryContainsCredentialValue_throwsBeforeCallingOpenAI() {
        LifeAreaMessage taintedPastMessage = LifeAreaMessage.builder()
                .conversation(conversation).role(MessageRole.USER)
                .content("복구코드: XY12-9988").build();
        when(lifeAreaMessageRepository.findByConversation_ConversationIdOrderByCreatedAtAscMessageIdAsc(CONVERSATION_ID))
                .thenReturn(List.of(taintedPastMessage));

        assertThatThrownBy(() -> conversationService.sendMessage(
                USER_ID, PLAN_ID, CONVERSATION_ID, "인스타그램 계정을 정리해줘"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SUSPECTED_CREDENTIAL_INPUT));

        verify(openAIClient, never()).getChatCompletion(any());
    }

    // md 스펙 #91 오탐 대응 기준 - 키워드만 있고 값이 없는 문장은 막지 않는다
    @Test
    void sendMessage_whenTextMentionsKeywordWithoutValue_proceedsToOpenAI() {
        OpenAIResponse response = new OpenAIResponse(List.of(
                new OpenAIResponse.Choice(new OpenAIMessageDto("assistant",
                        "{\"type\":\"QUESTION\",\"question\":\"어떤 계정인가요?\"}"))));
        when(openAIClient.getChatCompletion(any())).thenReturn(response);

        var result = conversationService.sendMessage(
                USER_ID, PLAN_ID, CONVERSATION_ID, "비밀번호는 지수가 알고 있어요");

        assertThat(result.type()).isEqualTo("QUESTION");
        verify(openAIClient).getChatCompletion(any());
    }

    @Test
    void sendMessage_whenNoCredentialAnywhere_savesAndCallsOpenAI() {
        OpenAIResponse response = new OpenAIResponse(List.of(
                new OpenAIResponse.Choice(new OpenAIMessageDto("assistant",
                        "{\"type\":\"QUESTION\",\"question\":\"어떤 계정인가요?\"}"))));
        when(openAIClient.getChatCompletion(any())).thenReturn(response);

        var result = conversationService.sendMessage(
                USER_ID, PLAN_ID, CONVERSATION_ID, "인스타그램 계정을 정리해줘");

        assertThat(result.type()).isEqualTo("QUESTION");
        // 사용자 발화 저장 1회 + AI 응답 원문 저장 1회
        verify(lifeAreaMessageRepository, times(2)).save(any());
        verify(openAIClient).getChatCompletion(any());
    }

    // issue #52 완료 조건 - "입력 상한 초과는 명확한 400... 으로 처리된다" (전체 이력 12,000자 상한)
    @Test
    void sendMessage_whenHistoryPlusNewMessageExceedsTwelveThousandChars_throwsWithoutSavingOrCallingOpenAI() {
        LifeAreaMessage longPastMessage = LifeAreaMessage.builder()
                .conversation(conversation).role(MessageRole.USER).content("x".repeat(11_990)).build();
        when(lifeAreaMessageRepository.findByConversation_ConversationIdOrderByCreatedAtAscMessageIdAsc(CONVERSATION_ID))
                .thenReturn(List.of(longPastMessage));

        assertThatThrownBy(() -> conversationService.sendMessage(
                USER_ID, PLAN_ID, CONVERSATION_ID, "x".repeat(11)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONVERSATION_HISTORY_TOO_LONG));

        verify(lifeAreaMessageRepository, never()).save(any());
        verify(openAIClient, never()).getChatCompletion(any());
    }

    @Test
    void sendMessage_whenHistoryPlusNewMessageIsExactlyTwelveThousandChars_proceeds() {
        LifeAreaMessage longPastMessage = LifeAreaMessage.builder()
                .conversation(conversation).role(MessageRole.USER).content("x".repeat(11_990)).build();
        when(lifeAreaMessageRepository.findByConversation_ConversationIdOrderByCreatedAtAscMessageIdAsc(CONVERSATION_ID))
                .thenReturn(List.of(longPastMessage));
        OpenAIResponse response = new OpenAIResponse(List.of(
                new OpenAIResponse.Choice(new OpenAIMessageDto("assistant",
                        "{\"type\":\"QUESTION\",\"question\":\"어떤 계정인가요?\"}"))));
        when(openAIClient.getChatCompletion(any())).thenReturn(response);

        conversationService.sendMessage(USER_ID, PLAN_ID, CONVERSATION_ID, "x".repeat(10));

        verify(openAIClient).getChatCompletion(any());
    }

    // issue #52 - "응답 JSON 구조를 서버에서 엄격히 검증". type이 QUESTION/RESULT가 아니면
    // 예전처럼 question=null인 애매한 응답으로 통과시키지 않고 명확히 실패해야 한다.
    @Test
    void sendMessage_whenAiResponseTypeIsUnrecognized_throwsAiResponseInvalid() {
        OpenAIResponse response = new OpenAIResponse(List.of(
                new OpenAIResponse.Choice(new OpenAIMessageDto("assistant", "{\"type\":\"UNKNOWN\"}"))));
        when(openAIClient.getChatCompletion(any())).thenReturn(response);

        assertThatThrownBy(() -> conversationService.sendMessage(
                USER_ID, PLAN_ID, CONVERSATION_ID, "인스타그램 계정을 정리해줘"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
    }

    @Test
    void sendMessage_whenAiResponseTypeResultHasNoItems_throwsAiResponseInvalid() {
        OpenAIResponse response = new OpenAIResponse(List.of(
                new OpenAIResponse.Choice(new OpenAIMessageDto("assistant", "{\"type\":\"RESULT\",\"items\":[]}"))));
        when(openAIClient.getChatCompletion(any())).thenReturn(response);

        assertThatThrownBy(() -> conversationService.sendMessage(
                USER_ID, PLAN_ID, CONVERSATION_ID, "인스타그램 계정을 정리해줘"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
    }

    @Test
    void sendMessage_whenAiResponseIsNotValidJson_throwsAiResponseInvalid() {
        OpenAIResponse response = new OpenAIResponse(List.of(
                new OpenAIResponse.Choice(new OpenAIMessageDto("assistant", "이건 JSON이 아닙니다"))));
        when(openAIClient.getChatCompletion(any())).thenReturn(response);

        assertThatThrownBy(() -> conversationService.sendMessage(
                USER_ID, PLAN_ID, CONVERSATION_ID, "인스타그램 계정을 정리해줘"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
    }
}
