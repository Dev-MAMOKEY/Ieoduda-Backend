package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
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
}
