package com.mamoki.ieojuda.domain.plan.service;

import tools.jackson.databind.ObjectMapper;
import com.mamoki.ieojuda.domain.plan.dto.AiStructuredItemDto;
import com.mamoki.ieojuda.domain.plan.dto.AiTurnResult;
import com.mamoki.ieojuda.domain.plan.dto.LifeAreaMessageHistoryResponse;
import com.mamoki.ieojuda.domain.plan.dto.LifeAreaMessageResponse;
import com.mamoki.ieojuda.domain.plan.dto.LifeAreaTurnResponse;
import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import com.mamoki.ieojuda.domain.plan.entity.LifeAreaMessage;
import com.mamoki.ieojuda.domain.plan.entity.MessageRole;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaMessageRepository;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaRepository;
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
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LifeAreaConversationService {

    private final LifeAreaRepository lifeAreaRepository;
    private final LifeAreaMessageRepository lifeAreaMessageRepository;
    private final ItemRepository itemRepository;
    private final OpenAIClient openAIClient;
    private final ObjectMapper objectMapper;

    // 대화 작성 화면 - 최신 턴부터 페이지 단위로 조회(무한 스크롤), 화면엔 오래된 순으로 뒤집어서 반환
    public LifeAreaMessageHistoryResponse getHistory(Long planId, Long lifeAreaId, Pageable pageable) {
        LifeArea lifeArea = findLifeArea(planId, lifeAreaId);

        Slice<LifeAreaMessage> slice = lifeAreaMessageRepository
                .findByLifeArea_LifeIdOrderByMessageIdDesc(lifeArea.getLifeId(), pageable);

        List<LifeAreaMessageResponse> messages = new ArrayList<>(
                slice.getContent().stream().map(LifeAreaMessageResponse::from).toList()
        );
        Collections.reverse(messages); // 최신순으로 가져온 걸 화면 렌더링 순서(오래된 순)로 뒤집음

        return LifeAreaMessageHistoryResponse.of(messages, slice.hasNext());
    }

    // 대화 작성 화면 - 사용자 발화 전송 -> AI의 다음 턴(질문 또는 구조화 결과) 반환
    @Transactional
    public LifeAreaTurnResponse sendMessage(Long planId, Long lifeAreaId, String userContent) {
        LifeArea lifeArea = findLifeArea(planId, lifeAreaId);

        // step 1. 지금까지의 대화 이력 로드
        List<LifeAreaMessage> history = lifeAreaMessageRepository
                .findByLifeArea_LifeIdOrderByMessageIdAsc(lifeArea.getLifeId());

        // step 2. 이번 사용자 메세지 저장
        LifeAreaMessage userMessage = lifeAreaMessageRepository.save(
                LifeAreaMessage.builder()
                        .lifeArea(lifeArea)
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

        // step 4. OpenAI 호출
        OpenAIResponse response = openAIClient.getChatCompletion(openAiHistory);
        String rawContent = response.choices().get(0).message().content();

        // step 5. AI 응답 저장 (원문 그대로 저장 - 다음 턴에도 이력으로 다시 전달되어야 함)
        lifeAreaMessageRepository.save(
                LifeAreaMessage.builder()
                        .lifeArea(lifeArea)
                        .role(MessageRole.ASSISTANT)
                        .content(rawContent)
                        .build()
        );

        // step 6. AI 응답 파싱 및 처리
        AiTurnResult turnResult = parseAiTurnResult(rawContent);

        if ("RESULT".equalsIgnoreCase(turnResult.type())) {
            // step 7. 구조화 완료 -> LifeArea에 결과 반영 + Item(PROPOSED) 생성
            lifeArea.applyAiStructuredResult(rawContent);
            List<Item> savedItems = createItems(lifeArea, turnResult.items());
            return new LifeAreaTurnResponse(
                    turnResult.type(),
                    null,
                    savedItems.stream().map(LifeAreaTurnResponse.ItemResponse::from).toList()
            );
        }

        // 아직 되묻는 중
        return new LifeAreaTurnResponse(turnResult.type(), turnResult.question(), List.of());
    }

    private List<Item> createItems(LifeArea lifeArea, List<AiStructuredItemDto> items) {
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

            Item item = Item.builder()
                    .lifeArea(lifeArea)
                    .locationType(dto.locationType())
                    .action(dto.action())
                    .precondition(dto.precondition())
                    .disclosureScope(disclosureScope)
                    .sourceExcerpt(dto.sourceExcerpt())
                    .build();
            savedItems.add(itemRepository.save(item));
        }
        return savedItems;
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

    private LifeArea findLifeArea(Long planId, Long lifeAreaId) {
        LifeArea lifeArea = lifeAreaRepository.findById(lifeAreaId)
                .orElseThrow(() -> new CustomException(ErrorCode.LIFE_AREA_NOT_FOUND));
        if (!lifeArea.getPlan().getPlanId().equals(planId)) {
            throw new CustomException(ErrorCode.LIFE_AREA_NOT_FOUND);
        }
        return lifeArea;
    }
}
