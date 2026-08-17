package com.mamoki.ieojuda.domain.plan.service;

import java.util.UUID;

import com.mamoki.ieojuda.domain.confirmer.repository.DisputeContactRepository;
import com.mamoki.ieojuda.domain.plan.dto.ReleasePolicyRequest;
import com.mamoki.ieojuda.domain.plan.dto.SelfWarningEmailRequest;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanStatus;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxService;
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

// 사용자 B가 사용자 A의 planId로 PlanController/PlanService 엔드포인트를 호출하면
// 전부 PLAN_NOT_FOUND(404)로 막혀야 한다는 수평 권한 상승(BOLA) 회귀 테스트
class PlanServiceBolaTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID ATTACKER_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();

    private PlanRepository planRepository;
    private DisputeContactRepository disputeContactRepository;
    private EmailOutboxService emailOutboxService;
    private PlanService planService;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        disputeContactRepository = mock(DisputeContactRepository.class);
        emailOutboxService = mock(EmailOutboxService.class);
        planService = new PlanService(
                planRepository,
                new PlanOwnershipReader(planRepository),
                disputeContactRepository,
                emailOutboxService,
                mock(AppProperties.class)
        );
        // 사용자 A의 계획만 존재한다 - 사용자 B(공격자)로 조회하면 항상 빈 Optional
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, ATTACKER_ID)).thenReturn(Optional.empty());
    }

    @Test
    void getPlanRejectsNonOwner() {
        assertThatThrownBy(() -> planService.getPlan(ATTACKER_ID, PLAN_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
    }

    @Test
    void deactivateRejectsNonOwner() {
        assertThatThrownBy(() -> planService.deactivate(ATTACKER_ID, PLAN_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
    }

    @Test
    void getReleaseSettingsRejectsNonOwnerAndDoesNotLeakDisputeContact() {
        assertThatThrownBy(() -> planService.getReleaseSettings(ATTACKER_ID, PLAN_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        verifyNoInteractions(disputeContactRepository);
    }

    @Test
    void updateReleasePolicyRejectsNonOwnerEvenWithAValidWaitingDaysValue() {
        // 소유자였다면 통과했을 유효한 값(14일)이어도, 소유자 검증이 범위 검증보다 먼저 막아야 한다
        assertThatThrownBy(() -> planService.updateReleasePolicy(ATTACKER_ID, PLAN_ID, new ReleasePolicyRequest(14)))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
    }

    @Test
    void requestSelfWarningEmailVerificationRejectsNonOwnerAndDoesNotSendEmail() {
        assertThatThrownBy(() -> planService.requestSelfWarningEmailVerification(
                ATTACKER_ID, PLAN_ID, new SelfWarningEmailRequest("attacker@example.com")))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        verifyNoInteractions(emailOutboxService);
    }

    @Test
    void getPlanReturnsThePlanForItsOwner() {
        Plan plan = mock(Plan.class);
        when(plan.getStatus()).thenReturn(PlanStatus.DRAFT);
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, OWNER_ID)).thenReturn(Optional.of(plan));

        assertThat(planService.getPlan(OWNER_ID, PLAN_ID)).isNotNull();
    }
}
