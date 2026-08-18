package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckAssigneeResponse;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckConfirmerResponse;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckStatusResponse;
import com.mamoki.ieojuda.domain.handoffcheck.service.HandoffCheckService;
import com.mamoki.ieojuda.domain.plan.dto.ItemResponse;
import com.mamoki.ieojuda.domain.plan.dto.LifeAreaResponse;
import com.mamoki.ieojuda.domain.plan.dto.OrderCheckItemResponse;
import com.mamoki.ieojuda.domain.plan.dto.OrderCheckResponse;
import com.mamoki.ieojuda.domain.plan.dto.PlanSummaryResponse;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanStatus;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.domain.releasecase.dto.ReleaseStatusResponse;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanSummaryServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();

    private PlanRepository planRepository;
    private LifeAreaService lifeAreaService;
    private ItemOrderService itemOrderService;
    private HandoffCheckService handoffCheckService;
    private ReleaseCaseRepository releaseCaseRepository;
    private PlanSummaryService planSummaryService;

    private Plan plan;
    private User user;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        lifeAreaService = mock(LifeAreaService.class);
        itemOrderService = mock(ItemOrderService.class);
        handoffCheckService = mock(HandoffCheckService.class);
        releaseCaseRepository = mock(ReleaseCaseRepository.class);
        planSummaryService = new PlanSummaryService(
                planRepository, lifeAreaService, itemOrderService, handoffCheckService, releaseCaseRepository);

        plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(PLAN_ID);
        when(plan.getStatus()).thenReturn(PlanStatus.DRAFT);
        when(plan.getWaitingDays()).thenReturn(14);
        user = User.builder().email("owner@test.com").password("hash").name("김나무").build();
        when(plan.getUser()).thenReturn(user);
        when(planRepository.findByUser_UserId(USER_ID)).thenReturn(Optional.of(plan));
    }

    @Test
    void getMySummary_throwsPlanNotFound_whenUserHasNoPlan() {
        when(planRepository.findByUser_UserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> planSummaryService.getMySummary(USER_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
    }

    @Test
    void getMySummary_aggregatesEveryCollaboratorResponse() {
        ItemResponse item = new ItemResponse(UUID.randomUUID(), "target", "location", "action", "title", "content",
                "", "FAMILY", "excerpt", "PROPOSED", 0, "OTHER", null);
        when(lifeAreaService.getLifeAreas(USER_ID, PLAN_ID)).thenReturn(List.of(
                new LifeAreaResponse("FAMILY", List.of(item, item, item)),
                new LifeAreaResponse("RELATIONSHIP_CLEANUP", List.of()),
                new LifeAreaResponse("WORK_CONTINUITY", List.of(item))
        ));

        HandoffCheckAssigneeResponse acceptedAssignee =
                new HandoffCheckAssigneeResponse(UUID.randomUUID(), "A", "FAMILY_MANAGER", true, true, "backup", true, null, true,
                        null, null, null, null, null, null);
        HandoffCheckAssigneeResponse pendingAssignee =
                new HandoffCheckAssigneeResponse(UUID.randomUUID(), "B", "WORK_MANAGER", true, false, null, false, null, false,
                        null, null, null, null, null, null);
        HandoffCheckConfirmerResponse acceptedConfirmer =
                new HandoffCheckConfirmerResponse(UUID.randomUUID(), "C", true, true, null, true);
        HandoffCheckConfirmerResponse pendingConfirmer =
                new HandoffCheckConfirmerResponse(UUID.randomUUID(), "D", false, false, null, false);
        when(handoffCheckService.getHandoffCheck(USER_ID, PLAN_ID)).thenReturn(HandoffCheckStatusResponse.of(
                List.of(acceptedAssignee, pendingAssignee), List.of(acceptedConfirmer, pendingConfirmer)));

        OrderCheckItemResponse conflicting = new OrderCheckItemResponse(
                UUID.randomUUID(), 0, "삭제 항목", "DELETE", "CLOUD_DELETE", "담당자", 24, "ACCEPTED", true, "충돌 사유", false, null);
        OrderCheckItemResponse clean = new OrderCheckItemResponse(
                UUID.randomUUID(), 1, "정상 항목", "OTHER", null, "담당자", 24, "ACCEPTED", false, null, false, null);
        when(itemOrderService.getOrderCheck(USER_ID, PLAN_ID))
                .thenReturn(OrderCheckResponse.of(List.of(conflicting, conflicting, clean)));

        ReleaseStatusResponse releaseStatus = ReleaseStatusResponse.of(null);
        when(releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(PLAN_ID)).thenReturn(Optional.empty());

        PlanSummaryResponse summary = planSummaryService.getMySummary(USER_ID);

        assertThat(summary.planId()).isEqualTo(PLAN_ID);
        assertThat(summary.status()).isEqualTo("DRAFT");
        assertThat(summary.userName()).isEqualTo("김나무");
        assertThat(summary.waitingDays()).isEqualTo(14);
        assertThat(summary.releaseCase()).isEqualTo(releaseStatus);

        assertThat(summary.lifeAreas()).hasSize(3);
        assertThat(summary.lifeAreas().get(0).itemCount()).isEqualTo(3);
        assertThat(summary.lifeAreas().get(0).isWritten()).isTrue();
        assertThat(summary.lifeAreas().get(1).itemCount()).isEqualTo(0);
        assertThat(summary.lifeAreas().get(1).isWritten()).isFalse();

        assertThat(summary.recipientAcceptedCount()).isEqualTo(1);
        assertThat(summary.recipientTotalCount()).isEqualTo(2);
        assertThat(summary.confirmerAcceptedCount()).isEqualTo(1);
        assertThat(summary.confirmerTotalCount()).isEqualTo(2);
        assertThat(summary.confirmerNames()).containsExactly("C", "D");
        assertThat(summary.backupMissingCount()).isEqualTo(1);
        assertThat(summary.unresolvedConflictCount()).isEqualTo(2);
    }

    @Test
    void getMySummary_whenNoRecipientsOrItems_returnsZeroedCounts() {
        when(lifeAreaService.getLifeAreas(USER_ID, PLAN_ID)).thenReturn(List.of(
                new LifeAreaResponse("FAMILY", List.of()),
                new LifeAreaResponse("RELATIONSHIP_CLEANUP", List.of()),
                new LifeAreaResponse("WORK_CONTINUITY", List.of())
        ));
        when(handoffCheckService.getHandoffCheck(USER_ID, PLAN_ID))
                .thenReturn(HandoffCheckStatusResponse.of(List.of(), List.of()));
        when(itemOrderService.getOrderCheck(USER_ID, PLAN_ID)).thenReturn(OrderCheckResponse.of(List.of()));
        when(releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(PLAN_ID)).thenReturn(Optional.empty());

        PlanSummaryResponse summary = planSummaryService.getMySummary(USER_ID);

        assertThat(summary.recipientAcceptedCount()).isZero();
        assertThat(summary.recipientTotalCount()).isZero();
        assertThat(summary.confirmerAcceptedCount()).isZero();
        assertThat(summary.confirmerTotalCount()).isZero();
        assertThat(summary.confirmerNames()).isEmpty();
        assertThat(summary.backupMissingCount()).isZero();
        assertThat(summary.unresolvedConflictCount()).isZero();
        assertThat(summary.lifeAreas()).allSatisfy(lifeArea -> {
            assertThat(lifeArea.itemCount()).isZero();
            assertThat(lifeArea.isWritten()).isFalse();
        });
        assertThat(summary.releaseCase().hasActiveCase()).isFalse();
    }
}
