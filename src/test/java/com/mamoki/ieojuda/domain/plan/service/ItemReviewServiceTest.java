package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.plan.dto.ItemReviewRequest;
import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.plan.entity.ItemActionType;
import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// issue #91 완료 조건 - "자격증명 의심 입력이 저장되지 않는다"의 승인 시점 방어선.
// 대화 저장 시점 검증(ConversationServiceTest)을 우회해 들어온 항목(예: 인라인 수정)도 승인 단계에서 다시 막아야 한다.
class ItemReviewServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();

    private ItemRepository itemRepository;
    private PlanOwnershipReader planOwnershipReader;
    private ItemReviewService itemReviewService;

    private Plan plan;
    private LifeArea lifeArea;

    @BeforeEach
    void setUp() {
        itemRepository = mock(ItemRepository.class);
        planOwnershipReader = mock(PlanOwnershipReader.class);
        itemReviewService = new ItemReviewService(itemRepository, planOwnershipReader);

        plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(PLAN_ID);
        lifeArea = mock(LifeArea.class);
        when(lifeArea.getPlan()).thenReturn(plan);
        when(planOwnershipReader.findOwnedPlan(USER_ID, PLAN_ID)).thenReturn(plan);
    }

    private Item buildItem(String action, String content, String locationType) {
        Item item = Item.builder()
                .lifeArea(lifeArea).targetName("대상").locationType(locationType).action(action)
                .title("제목").content(content).precondition("")
                .disclosureScope(DisclosureScope.RELATIONSHIP).sourceExcerpt("원문 근거")
                .sortOrder(0).actionType(ItemActionType.OTHER).build();
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
        return item;
    }

    @Test
    void approve_whenActionContainsCredentialValue_throwsSuspectedCredentialInput() {
        buildItem("인스타그램 비밀번호는 abcd1234야", "비공개 전환", "인스타그램");

        assertThatThrownBy(() -> itemReviewService.approve(USER_ID, PLAN_ID, new ItemReviewRequest(ITEM_ID)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SUSPECTED_CREDENTIAL_INPUT));
    }

    @Test
    void approve_whenContentContainsCredentialValue_throwsSuspectedCredentialInput() {
        buildItem("계정 정리", "PIN 5678로 해제 후 탈퇴", "인스타그램");

        assertThatThrownBy(() -> itemReviewService.approve(USER_ID, PLAN_ID, new ItemReviewRequest(ITEM_ID)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SUSPECTED_CREDENTIAL_INPUT));
    }

    @Test
    void approve_whenLocationTypeContainsCredentialValue_throwsSuspectedCredentialInput() {
        buildItem("계정 정리", "탈퇴 처리", "복구코드: XY12-9988");

        assertThatThrownBy(() -> itemReviewService.approve(USER_ID, PLAN_ID, new ItemReviewRequest(ITEM_ID)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SUSPECTED_CREDENTIAL_INPUT));
    }

    // md 스펙 #91 오탐 대응 기준 - 키워드만 있고 값이 없는 문장은 승인을 막지 않는다
    @Test
    void approve_whenTextMentionsKeywordWithoutValue_approvesNormally() {
        Item item = buildItem("비밀번호는 지수가 알고 있어요", "탈퇴 처리", "인스타그램");

        var response = itemReviewService.approve(USER_ID, PLAN_ID, new ItemReviewRequest(ITEM_ID));

        assertThat(response.itemId()).isEqualTo(item.getItemId());
    }

    @Test
    void approve_whenNoCredentialAndHasSourceExcerpt_approvesNormally() {
        buildItem("계정 정리", "비공개 전환", "인스타그램");

        var response = itemReviewService.approve(USER_ID, PLAN_ID, new ItemReviewRequest(ITEM_ID));

        assertThat(response).isNotNull();
    }
}
