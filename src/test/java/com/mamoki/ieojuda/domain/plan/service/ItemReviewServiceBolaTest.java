package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.plan.dto.ItemReviewRequest;
import com.mamoki.ieojuda.domain.plan.dto.ItemUpdateRequest;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 사용자 B가 사용자 A의 planId로 항목 조회/승인/수정/삭제를 시도하면 PLAN_NOT_FOUND로 막혀야 한다.
// delete는 실제 삭제(파괴적)이므로, 거부됐을 때 itemRepository에 전혀 손대지 않는지가 특히 중요하다.
class ItemReviewServiceBolaTest {

    private static final Long OWNER_ID = 1L;
    private static final Long ATTACKER_ID = 2L;
    private static final Long PLAN_ID = 10L;
    private static final Long ITEM_ID = 100L;

    private PlanRepository planRepository;
    private ItemRepository itemRepository;
    private ItemReviewService itemReviewService;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        itemRepository = mock(ItemRepository.class);
        itemReviewService = new ItemReviewService(itemRepository, new PlanOwnershipReader(planRepository));
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, ATTACKER_ID)).thenReturn(Optional.empty());
    }

    @Test
    void getItemRejectsNonOwnerAndDoesNotTouchItemRepository() {
        assertThatThrownBy(() -> itemReviewService.getItem(ATTACKER_ID, PLAN_ID, ITEM_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        verifyNoInteractions(itemRepository);
    }

    @Test
    void approveRejectsNonOwnerAndDoesNotTouchItemRepository() {
        assertThatThrownBy(() -> itemReviewService.approve(ATTACKER_ID, PLAN_ID, new ItemReviewRequest(ITEM_ID)))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        verifyNoInteractions(itemRepository);
    }

    @Test
    void updateRejectsNonOwnerAndDoesNotTouchItemRepository() {
        ItemUpdateRequest request = new ItemUpdateRequest(
                "대상", "위치", "행동", "제목", "내용", null, "FAMILY", "OTHER");

        assertThatThrownBy(() -> itemReviewService.update(ATTACKER_ID, PLAN_ID, ITEM_ID, request))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        verifyNoInteractions(itemRepository);
    }

    @Test
    void deleteRejectsNonOwnerAndNeverDeletesTheItem() {
        assertThatThrownBy(() -> itemReviewService.delete(ATTACKER_ID, PLAN_ID, ITEM_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        verifyNoInteractions(itemRepository);
    }
}
