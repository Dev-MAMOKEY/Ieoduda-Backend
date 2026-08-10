package com.mamoki.ieojuda.domain.plan.service;

import tools.jackson.databind.ObjectMapper;
import com.mamoki.ieojuda.domain.plan.dto.AiStructuredItemDto;
import com.mamoki.ieojuda.domain.plan.dto.AiTurnResult;
import com.mamoki.ieojuda.domain.plan.dto.ConversationResponse;
import com.mamoki.ieojuda.domain.plan.dto.ItemResponse;
import com.mamoki.ieojuda.domain.plan.dto.LifeAreaMessageHistoryResponse;
import com.mamoki.ieojuda.domain.plan.dto.LifeAreaMessageResponse;
import com.mamoki.ieojuda.domain.plan.dto.LifeAreaTurnResponse;
import com.mamoki.ieojuda.domain.plan.entity.Conversation;
import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.plan.entity.ItemStatus;
import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import com.mamoki.ieojuda.domain.plan.entity.LifeAreaCategory;
import com.mamoki.ieojuda.domain.plan.entity.LifeAreaMessage;
import com.mamoki.ieojuda.domain.plan.entity.MessageRole;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.ConversationRepository;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaMessageRepository;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.openai.component.OpenAIClient;
import com.mamoki.ieojuda.global.openai.dto.OpenAIMessageDto;
import com.mamoki.ieojuda.global.openai.dto.OpenAIResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// 명세서 "삶의 구역 생성" 화면 - 사용자가 자유롭게 채팅하면 AI가 대상·주제별로 알아서 나눠 항목화한다.
// "계획 만들기" 폼이 없어졌으므로 첫 메세지부터 이 서비스 하나로 처리한다.
// 대화는 세션(Conversation) 단위로 나뉜다 - 처음 채팅한 세션과 나중에 수정하러 들어온 세션을 구분해서 기록하기 위함.
// 다만 항목이 쌓이는 카테고리(LifeArea)는 세션과 무관하게 계획(Plan) 전체 기준으로 계속 누적된다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationService {

    private final PlanRepository planRepository;
    private final ConversationRepository conversationRepository;
    private final LifeAreaMessageRepository lifeAreaMessageRepository;
    private final LifeAreaRepository lifeAreaRepository;
    private final ItemRepository itemRepository;
    private final OpenAIClient openAIClient;
    private final ObjectMapper objectMapper;

    // 새 대화 세션 시작 - 처음 채팅을 시작할 때, 또는 나중에 수정하러 다시 들어올 때마다 호출
    @Transactional
    public ConversationResponse startConversation(Long planId) {
        Plan plan = findPlan(planId);
        Conversation conversation = conversationRepository.save(Conversation.builder().plan(plan).build());
        return ConversationResponse.from(conversation);
    }

    // 대화 작성 화면 - 최신 턴부터 페이지 단위로 조회(무한 스크롤), 화면엔 오래된 순으로 뒤집어서 반환
    public LifeAreaMessageHistoryResponse getHistory(Long planId, Long conversationId, Pageable pageable) {
        Conversation conversation = findConversation(planId, conversationId);

        Slice<LifeAreaMessage> slice = lifeAreaMessageRepository
                .findByConversation_ConversationIdOrderByMessageIdDesc(conversation.getConversationId(), pageable);

        List<LifeAreaMessageResponse> messages = new ArrayList<>(
                slice.getContent().stream().map(LifeAreaMessageResponse::from).toList()
        );
        Collections.reverse(messages); // 최신순으로 가져온 걸 화면 렌더링 순서(오래된 순)로 뒤집음

        return LifeAreaMessageHistoryResponse.of(messages, slice.hasNext());
    }

    // 대화 작성 화면 - 사용자 발화 전송 -> AI의 다음 턴(질문 또는 구조화 결과) 반환
    @Transactional
    public LifeAreaTurnResponse sendMessage(Long planId, Long conversationId, String userContent) {
        Conversation conversation = findConversation(planId, conversationId);
        Plan plan = conversation.getPlan();

        // step 1. 이 세션 안에서 지금까지의 대화 이력 로드
        List<LifeAreaMessage> history = lifeAreaMessageRepository
                .findByConversation_ConversationIdOrderByMessageIdAsc(conversation.getConversationId());

        // step 2. 이번 사용자 메세지 저장
        LifeAreaMessage userMessage = lifeAreaMessageRepository.save(
                LifeAreaMessage.builder()
                        .conversation(conversation)
                        .role(MessageRole.USER)
                        .content(userContent)
                        .build()
        );

        // step 3. OpenAI에 보낼 메세지 목록 구성 (이전 이력 + 이번 사용자 메세지)
        List<OpenAIMessageDto> openAiHistory = new ArrayList<>();
        for (LifeAreaMessage message : history) {
            openAiHistory.add(new OpenAIMessageDto(toOpenAiRole(message.getRole()), message.getContent()));
        }
        openAiHistory.add(new OpenAIMessageDto(toOpenAiRole(MessageRole.USER), userMessage.getContent()));

        // step 4. OpenAI 호출 - 계획 만들기 폼이 없어졌으므로 별도 seedContext 없이 대화 이력만으로 판단
        OpenAIResponse response = openAIClient.getChatCompletion(openAiHistory);
        String rawContent = response.choices().get(0).message().content();

        // step 5. AI 응답 저장 (원문 그대로 저장 - 다음 턴에도 이력으로 다시 전달되어야 함)
        lifeAreaMessageRepository.save(
                LifeAreaMessage.builder()
                        .conversation(conversation)
                        .role(MessageRole.ASSISTANT)
                        .content(rawContent)
                        .build()
        );

        // step 6. AI 응답 파싱 및 처리
        AiTurnResult turnResult = parseAiTurnResult(rawContent);

        if ("RESULT".equalsIgnoreCase(turnResult.type())) {
            // step 7. 구조화 완료 -> 이번 턴에서 나온 카테고리별로 LifeArea를 새로 만들고 Item(PROPOSED) 생성
            List<Item> savedItems = createItems(plan, conversation, turnResult.items());
            return new LifeAreaTurnResponse(
                    turnResult.type(),
                    null,
                    savedItems.stream().map(ItemResponse::from).toList()
            );
        }

        // 아직 되묻는 중
        return new LifeAreaTurnResponse(turnResult.type(), turnResult.question(), List.of());
    }

    private List<Item> createItems(Plan plan, Conversation conversation, List<AiStructuredItemDto> items) {
        // 대화가 이어지며 AI가 다시 구조화할 때마다, 아직 검토 안 된(PROPOSED) 이전 초안은 계획 전체 기준으로 지우고
        // 새로 받은 항목으로 교체한다. 이미 사용자가 승인/기각한 항목(APPROVED/REJECTED)은 그대로 둔다.
        List<Item> previousProposed = itemRepository.findByLifeArea_Plan_PlanIdAndStatus(plan.getPlanId(), ItemStatus.PROPOSED);
        itemRepository.deleteAll(previousProposed);

        // 카테고리마다 여러 LifeArea 행이 있을 수 있다(대화 세션/턴마다 새로 생김) -
        // 이번 턴 안에서는 같은 카테고리끼리 하나의 LifeArea를 공유하고, 다음 턴에는 또 새로 만든다.
        Map<LifeAreaCategory, LifeArea> lifeAreasThisTurn = new EnumMap<>(LifeAreaCategory.class);

        List<Item> savedItems = new ArrayList<>();
        for (AiStructuredItemDto dto : items) {
            // 명세서 "AI 구조화 결과 검토" 예외 처리: 원문 근거 없는 항목은 승인 불가
            if (dto.sourceExcerpt() == null || dto.sourceExcerpt().isBlank()) {
                throw new CustomException(ErrorCode.UNGROUNDED_ITEM_NOT_APPROVABLE);
            }

            DisclosureScope disclosureScope;
            try {
                disclosureScope = DisclosureScope.valueOf(dto.disclosureScope());
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            }

            LifeAreaCategory category = toLifeAreaCategory(disclosureScope);
            LifeArea lifeArea = lifeAreasThisTurn.computeIfAbsent(category, key ->
                    lifeAreaRepository.save(LifeArea.builder().plan(plan).conversation(conversation).category(key).build()));

            Item item = Item.builder()
                    .lifeArea(lifeArea)
                    .targetName(dto.targetName())
                    .locationType(dto.locationType())
                    .action(dto.action())
                    .precondition(dto.precondition())
                    .disclosureScope(disclosureScope)
                    .sourceExcerpt(dto.sourceExcerpt())
                    .sortOrder(savedItems.size())
                    .build();
            savedItems.add(itemRepository.save(item));
        }
        return savedItems;
    }

    private LifeAreaCategory toLifeAreaCategory(DisclosureScope disclosureScope) {
        return switch (disclosureScope) {
            case FAMILY -> LifeAreaCategory.FAMILY;
            case WORK -> LifeAreaCategory.WORK_CONTINUITY;
            case RELATIONSHIP -> LifeAreaCategory.RELATIONSHIP_CLEANUP;
        };
    }

    private AiTurnResult parseAiTurnResult(String rawContent) {
        try {
            return objectMapper.readValue(rawContent, AiTurnResult.class);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String toOpenAiRole(MessageRole role) {
        return role == MessageRole.ASSISTANT ? "assistant" : "user";
    }

    private Plan findPlan(Long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAN_NOT_FOUND));
    }

    private Conversation findConversation(Long planId, Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONVERSATION_NOT_FOUND));
        if (!conversation.getPlan().getPlanId().equals(planId)) {
            throw new CustomException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }
}
