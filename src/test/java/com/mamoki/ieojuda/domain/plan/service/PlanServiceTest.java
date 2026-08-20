package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.confirmer.repository.DisputeContactRepository;
import com.mamoki.ieojuda.domain.plan.dto.ReleasePolicyRequest;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxService;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlanServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();

    private PlanRepository planRepository;
    private Plan plan;
    private PlanService planService;
    private Validator validator;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        plan = mock(Plan.class);
        planService = new PlanService(
                planRepository,
                new PlanOwnershipReader(planRepository),
                mock(DisputeContactRepository.class),
                mock(EmailOutboxService.class),
                mock(AppProperties.class)
        );
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @ParameterizedTest
    @MethodSource("validWaitingDays")
    void updateReleasePolicyAcceptsEveryDayWithinRange(int waitingDays) {
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(plan));

        planService.updateReleasePolicy(USER_ID, PLAN_ID, new ReleasePolicyRequest(waitingDays));

        verify(plan).updateWaitingDays(waitingDays);
    }

    private static Stream<Integer> validWaitingDays() {
        return IntStream.rangeClosed(7, 30).boxed();
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 31})
    void updateReleasePolicyRejectsDaysOutsideRange(int waitingDays) {
        assertInvalidWaitingDays(waitingDays);
    }

    @Test
    void updateReleasePolicyRejectsNull() {
        assertInvalidWaitingDays(null);
    }

    private void assertInvalidWaitingDays(Integer waitingDays) {
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> planService.updateReleasePolicy(USER_ID, PLAN_ID, new ReleasePolicyRequest(waitingDays)))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_WAITING_PERIOD));
        verify(plan, org.mockito.Mockito.never()).updateWaitingDays(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void requestValidationMatchesSevenToThirtyDayRange() {
        assertThat(validator.validate(new ReleasePolicyRequest(7))).isEmpty();
        assertThat(validator.validate(new ReleasePolicyRequest(30))).isEmpty();
        assertThat(validator.validate(new ReleasePolicyRequest(6))).isNotEmpty();
        assertThat(validator.validate(new ReleasePolicyRequest(31))).isNotEmpty();
        assertThat(validator.validate(new ReleasePolicyRequest(null))).isNotEmpty();
    }
}
