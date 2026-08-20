package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanStatus;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
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

class PlanOwnershipReaderTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final UUID NONEXISTENT_PLAN_ID = UUID.randomUUID();

    private PlanRepository planRepository;
    private PlanOwnershipReader planOwnershipReader;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        planOwnershipReader = new PlanOwnershipReader(planRepository);
    }

    private Plan planWithStatus(PlanStatus status) {
        Plan plan = mock(Plan.class);
        when(plan.getStatus()).thenReturn(status);
        return plan;
    }

    @Test
    void returnsThePlanWhenTheCallerIsTheOwner() {
        Plan plan = mock(Plan.class);
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, OWNER_ID)).thenReturn(Optional.of(plan));

        Plan result = planOwnershipReader.findOwnedPlan(OWNER_ID, PLAN_ID);

        assertThat(result).isSameAs(plan);
    }

    @Test
    void throwsPlanNotFoundWhenTheCallerIsNotTheOwner() {
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, OTHER_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> planOwnershipReader.findOwnedPlan(OTHER_USER_ID, PLAN_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
    }

    @Test
    void throwsTheSamePlanNotFoundWhenThePlanDoesNotExistAtAll() {
        when(planRepository.findByPlanIdAndUser_UserId(NONEXISTENT_PLAN_ID, OWNER_ID)).thenReturn(Optional.empty());

        // 존재하지 않는 planId와 타인 소유 planId가 같은 예외를 던져야 존재 여부가 노출되지 않는다
        assertThatThrownBy(() -> planOwnershipReader.findOwnedPlan(OWNER_ID, NONEXISTENT_PLAN_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
    }

    // 버그 회귀 방지 - 계획을 비활성화해도 findOwnedPlan()을 쓰는 하위 서비스들은 PlanStatus를 전혀
    // 보지 않아서 담당자·항목 등이 계속 조회되던 문제. findOwnedActivePlan()이 그 방어선 역할을 한다.

    @Test
    void findOwnedPlan_doesNotCareAboutStatus_evenWhenDeactivated() {
        // 계획 요약(마이페이지, 계획 홈)처럼 비활성화 여부와 무관하게 상태 자체를 보여줘야 하는 화면은
        // 이 메서드를 그대로 써야 한다 - 일부러 상태를 검사하지 않는다.
        Plan deactivated = planWithStatus(PlanStatus.DEACTIVATED);
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, OWNER_ID)).thenReturn(Optional.of(deactivated));

        Plan result = planOwnershipReader.findOwnedPlan(OWNER_ID, PLAN_ID);

        assertThat(result).isSameAs(deactivated);
    }

    @Test
    void findOwnedActivePlan_whenDeactivated_throwsPlanDeactivated() {
        Plan deactivated = planWithStatus(PlanStatus.DEACTIVATED);
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, OWNER_ID)).thenReturn(Optional.of(deactivated));

        assertThatThrownBy(() -> planOwnershipReader.findOwnedActivePlan(OWNER_ID, PLAN_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PLAN_DEACTIVATED));
    }

    @Test
    void findOwnedActivePlan_whenDraftOrSealed_succeeds() {
        Plan draft = planWithStatus(PlanStatus.DRAFT);
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, OWNER_ID)).thenReturn(Optional.of(draft));
        assertThat(planOwnershipReader.findOwnedActivePlan(OWNER_ID, PLAN_ID)).isSameAs(draft);

        Plan sealed = planWithStatus(PlanStatus.SEALED);
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, OWNER_ID)).thenReturn(Optional.of(sealed));
        assertThat(planOwnershipReader.findOwnedActivePlan(OWNER_ID, PLAN_ID)).isSameAs(sealed);
    }

    @Test
    void findOwnedActivePlan_whenNotOwner_throwsPlanNotFound() {
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, OTHER_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> planOwnershipReader.findOwnedActivePlan(OTHER_USER_ID, PLAN_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
    }
}
