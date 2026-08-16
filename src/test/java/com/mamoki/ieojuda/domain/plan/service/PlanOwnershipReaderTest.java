package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanOwnershipReaderTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long PLAN_ID = 10L;

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
        when(planRepository.findByPlanIdAndUser_UserId(999L, OWNER_ID)).thenReturn(Optional.empty());

        // 존재하지 않는 planId와 타인 소유 planId가 같은 예외를 던져야 존재 여부가 노출되지 않는다
        assertThatThrownBy(() -> planOwnershipReader.findOwnedPlan(OWNER_ID, 999L))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
    }
}
