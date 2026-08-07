package com.mamoki.ieojuda.domain.plan.service;

import tools.jackson.databind.ObjectMapper;
import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.plan.dto.AiStructuredItemDto;
import com.mamoki.ieojuda.domain.plan.dto.InitialStructureResult;
import com.mamoki.ieojuda.domain.plan.dto.LifeAreaResponse;
import com.mamoki.ieojuda.domain.plan.dto.LifeAreaTurnResponse;
import com.mamoki.ieojuda.domain.plan.dto.PlanCreateRequest;
import com.mamoki.ieojuda.domain.plan.dto.PlanResponse;
import com.mamoki.ieojuda.domain.plan.dto.PlanUpdateRequest;
import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import com.mamoki.ieojuda.domain.plan.entity.LifeAreaCategory;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.openai.component.OpenAIClient;
import com.mamoki.ieojuda.global.openai.dto.OpenAIResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// 명세서 "새 계획 만들기" / "계획 홈" / "마이페이지" 화면 - 계획 생성·조회·수정·비활성화
// "새 계획 만들기" 화면은 한 화면·한 버튼("세부사항 대화하기")으로 계획 정보와 구역별 입력값을 함께 제출한다.
// 어느 입력 필드에 담겼는지가 아니라 AI가 실제 내용을 보고 가족/관계정리/업무연속성 중 어디에 속하는지 판단해서
// 항목(Item)까지 만들어 반환하고, 사용자는 그 결과를 승인/기각(POST .../items/review)하면 된다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanService {

    private static final int MIN_WAITING_DAYS = 7;
    private static final int MAX_WAITING_DAYS = 30;

    private final PlanRepository planRepository;
    private final LifeAreaRepository lifeAreaRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final OpenAIClient openAIClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public PlanResponse create(PlanCreateRequest request) {
        validateWaitingDays(request.waitingDays());

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Plan plan = planRepository.save(Plan.builder()
                .user(user)
                .name(request.name())
                .waitingDays(request.waitingDays())
                .build());

        // 명세서 "새 계획 만들기" 표시 요소: 기본 구역(가족/관계 정리/업무 연속성)을 먼저 생성
        // (item을 붙이려면 life_id가 먼저 있어야 함). 입력값 전체를 그대로 각 구역의 rawText로 남겨서,
        // 이후 "삶의 구역 생성" 채팅에서도 AI가 전체 맥락을 계속 참고할 수 있게 한다.
        String optionsJson = toJson(new PlanOptionsSummary(request));
        Map<LifeAreaCategory, LifeArea> lifeAreasByCategory = new EnumMap<>(LifeAreaCategory.class);
        for (LifeAreaCategory category : LifeAreaCategory.values()) {
            LifeArea lifeArea = LifeArea.builder().plan(plan).category(category).build();
            lifeArea.applyRawText(optionsJson);
            lifeAreasByCategory.put(category, lifeAreaRepository.save(lifeArea));
        }

        // 어느 칸에 썼는지가 아니라 AI가 내용을 보고 3개 구역 중 실제로 맞는 곳으로 분류/구조화
        Map<LifeAreaCategory, List<LifeAreaTurnResponse.ItemResponse>> itemsByCategory = createInitialItems(optionsJson, lifeAreasByCategory);

        List<LifeAreaResponse> lifeAreaResponses = new ArrayList<>();
        for (LifeAreaCategory category : LifeAreaCategory.values()) {
            lifeAreaResponses.add(LifeAreaResponse.from(
                    lifeAreasByCategory.get(category),
                    itemsByCategory.getOrDefault(category, List.of())
            ));
        }

        return PlanResponse.from(plan, lifeAreaResponses);
    }

    public PlanResponse getPlan(Long planId) {
        return PlanResponse.from(findPlan(planId));
    }

    @Transactional
    public PlanResponse update(Long planId, PlanUpdateRequest request) {
        validateWaitingDays(request.waitingDays());
        Plan plan = findPlan(planId);
        plan.updateInfo(request.name(), request.waitingDays());
        return PlanResponse.from(plan);
    }

    @Transactional
    public PlanResponse deactivate(Long planId) {
        Plan plan = findPlan(planId);
        plan.deactivate();
        return PlanResponse.from(plan);
    }

    private Map<LifeAreaCategory, List<LifeAreaTurnResponse.ItemResponse>> createInitialItems(
            String optionsJson, Map<LifeAreaCategory, LifeArea> lifeAreasByCategory) {
        OpenAIResponse response = openAIClient.getInitialStructure(optionsJson);
        String rawContent = response.choices().get(0).message().content();
        InitialStructureResult result = parseInitialStructure(rawContent);

        Map<LifeAreaCategory, List<LifeAreaTurnResponse.ItemResponse>> itemsByCategory = new EnumMap<>(LifeAreaCategory.class);
        for (AiStructuredItemDto dto : result.items()) {
            // 명세서 "AI 구조화 결과 검토" 예외 처리: 근거 없는 항목은 승인 불가 (여기선 생성 자체를 차단)
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
            LifeArea lifeArea = lifeAreasByCategory.get(category);

            Item item = Item.builder()
                    .lifeArea(lifeArea)
                    .targetName(dto.targetName())
                    .locationType(dto.locationType())
                    .action(dto.action())
                    .precondition(dto.precondition())
                    .disclosureScope(disclosureScope)
                    .sourceExcerpt(dto.sourceExcerpt())
                    .build();
            Item saved = itemRepository.save(item);

            itemsByCategory.computeIfAbsent(category, key -> new ArrayList<>())
                    .add(LifeAreaTurnResponse.ItemResponse.from(saved));
        }
        return itemsByCategory;
    }

    private LifeAreaCategory toLifeAreaCategory(DisclosureScope disclosureScope) {
        return switch (disclosureScope) {
            case FAMILY -> LifeAreaCategory.FAMILY;
            case WORK -> LifeAreaCategory.WORK_CONTINUITY;
            case RELATIONSHIP -> LifeAreaCategory.RELATIONSHIP_CLEANUP;
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private InitialStructureResult parseInitialStructure(String rawContent) {
        try {
            return objectMapper.readValue(rawContent, InitialStructureResult.class);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void validateWaitingDays(Integer waitingDays) {
        // 명세서 예외 처리: 7일 미만 또는 30일 초과 값을 저장하지 않음
        if (waitingDays == null || waitingDays < MIN_WAITING_DAYS || waitingDays > MAX_WAITING_DAYS) {
            throw new CustomException(ErrorCode.INVALID_WAITING_PERIOD);
        }
    }

    private Plan findPlan(Long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAN_NOT_FOUND));
    }

    // AI에게 보낼 입력값 전체 요약 (계획 이름/대기기간 등 AI 판단과 무관한 값은 제외)
    private record PlanOptionsSummary(String familyMessage,
                                       Object snsAction, String snsOtherDetail, Object obituaryDelivery,
                                       Object workAccountAction, Object ongoingWorkHandover) {
        PlanOptionsSummary(PlanCreateRequest request) {
            this(request.familyMessage(),
                    request.snsAction(), request.snsOtherDetail(), request.obituaryDelivery(),
                    request.workAccountAction(), request.ongoingWorkHandover());
        }
    }
}
