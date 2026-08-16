package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.plan.repository.ConversationRepository;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaMessageRepository;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.openai.component.OpenAIClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 사용자 B가 사용자 A의 planId로 대화 세션 시작/이력조회/발화전송을 시도하면 PLAN_NOT_FOUND로 막혀야 한다.
// 특히 sendMessage는 성공 시 OpenAI 호출 + 항목 삭제/생성이라는 부수효과가 크므로, 거부됐을 때 그 부수효과가 전혀 없어야 한다.
class ConversationServiceBolaTest {

    private static final Long OWNER_ID = 1L;
    private static final Long ATTACKER_ID = 2L;
    private static final Long PLAN_ID = 10L;
    private static final Long CONVERSATION_ID = 100L;

    private PlanRepository planRepository;
    private ConversationRepository conversationRepository;
    private ItemRepository itemRepository;
    private OpenAIClient openAIClient;
    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        conversationRepository = mock(ConversationRepository.class);
        itemRepository = mock(ItemRepository.class);
        openAIClient = mock(OpenAIClient.class);
        conversationService = new ConversationService(
                new PlanOwnershipReader(planRepository),
                conversationRepository,
                mock(LifeAreaMessageRepository.class),
                mock(LifeAreaRepository.class),
                itemRepository,
                openAIClient,
                mock(ObjectMapper.class)
        );
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, ATTACKER_ID)).thenReturn(Optional.empty());
    }

    @Test
    void startConversationRejectsNonOwnerAndDoesNotCreateASession() {
        assertThatThrownBy(() -> conversationService.startConversation(ATTACKER_ID, PLAN_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        verifyNoInteractions(conversationRepository);
    }

    @Test
    void getHistoryRejectsNonOwnerBeforeLookingUpTheConversation() {
        assertThatThrownBy(() -> conversationService.getHistory(
                ATTACKER_ID, PLAN_ID, CONVERSATION_ID, PageRequest.of(0, 20)))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        verifyNoInteractions(conversationRepository);
    }

    @Test
    void sendMessageRejectsNonOwnerAndNeverCallsOpenAiOrDeletesItems() {
        assertThatThrownBy(() -> conversationService.sendMessage(ATTACKER_ID, PLAN_ID, CONVERSATION_ID, "안녕하세요"))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        verifyNoInteractions(conversationRepository);
        verifyNoInteractions(openAIClient);
        verifyNoInteractions(itemRepository);
    }
}
