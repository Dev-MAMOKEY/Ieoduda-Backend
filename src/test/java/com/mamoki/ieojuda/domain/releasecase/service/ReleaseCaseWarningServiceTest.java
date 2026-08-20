package com.mamoki.ieojuda.domain.releasecase.service;

import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.confirmer.entity.DisputeContact;
import com.mamoki.ieojuda.domain.confirmer.repository.DisputeContactRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.outbox.CriticalEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 이 작업의 핵심 회귀 테스트 - "대기 시작 전에 경고가 전송된다"와 "발송 실패가 성공으로 기록되지 않는다"를
// 코드 레벨에서 보장한다: 경고 발송이 실패하면 WAITING 전이가 절대 일어나지 않고 사건이 동결되어야 한다.
class ReleaseCaseWarningServiceTest {

    private static final UUID CASE_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();

    private DisputeContactRepository disputeContactRepository;
    private SecurityTokenService securityTokenService;
    private CriticalEmailSender criticalEmailSender;
    private AppProperties appProperties;
    private ReleaseCaseWarningService releaseCaseWarningService;

    private ReleaseCase releaseCase;
    private Plan plan;

    @BeforeEach
    void setUp() {
        disputeContactRepository = mock(DisputeContactRepository.class);
        securityTokenService = mock(SecurityTokenService.class);
        criticalEmailSender = mock(CriticalEmailSender.class);
        appProperties = mock(AppProperties.class);
        releaseCaseWarningService = new ReleaseCaseWarningService(
                disputeContactRepository, securityTokenService, criticalEmailSender, appProperties);

        plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(PLAN_ID);
        when(plan.getSelfWarningEmail()).thenReturn("author@test.com");
        releaseCase = mock(ReleaseCase.class);
        when(releaseCase.getCaseId()).thenReturn(CASE_ID);
        when(releaseCase.getPlan()).thenReturn(plan);

        when(appProperties.getBaseUrl()).thenReturn("https://ieoduda.example.com");
        when(appProperties.getContactEmail()).thenReturn("support@ieoduda.example.com");
        when(securityTokenService.issueForCase(any(), any(), any())).thenReturn("cancel-token");
        when(securityTokenService.issueForDisputeContact(any(), any(), any(), any())).thenReturn("dispute-token");
    }

    @Test
    void sendAuthorCancelWarningOrFreeze_whenSendSucceeds_doesNotFreeze() {
        when(criticalEmailSender.sendOrFail(eq(plan), eq(EmailType.CASE_CANCEL_WARNING), eq("author@test.com"), any()))
                .thenReturn(true);

        boolean result = releaseCaseWarningService.sendAuthorCancelWarningOrFreeze(releaseCase);

        assertThat(result).isTrue();
        verify(releaseCase, never()).freeze();
    }

    @Test
    void sendAuthorCancelWarningOrFreeze_whenSendFails_freezesCase() {
        when(criticalEmailSender.sendOrFail(eq(plan), eq(EmailType.CASE_CANCEL_WARNING), eq("author@test.com"), any()))
                .thenReturn(false);

        boolean result = releaseCaseWarningService.sendAuthorCancelWarningOrFreeze(releaseCase);

        assertThat(result).isFalse();
        verify(releaseCase).freeze();
    }

    @Test
    void sendDisputeWarningsAndStartWaiting_whenAllVerifiedContactsSucceed_startsWaiting() {
        DisputeContact verified = mock(DisputeContact.class);
        when(verified.getIsVerified()).thenReturn(true);
        when(verified.getPlan()).thenReturn(plan);
        when(verified.getEmail()).thenReturn("dispute@test.com");
        when(disputeContactRepository.findByPlan_PlanId(PLAN_ID)).thenReturn(List.of(verified));
        when(criticalEmailSender.sendOrFail(eq(plan), eq(EmailType.OBJECTION_WINDOW_NOTICE), eq("dispute@test.com"), any()))
                .thenReturn(true);

        boolean result = releaseCaseWarningService.sendDisputeWarningsAndStartWaiting(releaseCase, 7);

        assertThat(result).isTrue();
        verify(releaseCase).approveEvidenceAndStartWaiting(7);
        verify(securityTokenService).revokeAllForCase(releaseCase, SecurityTokenPurpose.UPLOAD_EVIDENCE);
        verify(releaseCase, never()).freeze();
    }

    // 핵심 회귀 - 이의 연락처 중 한 명이라도 발송에 실패하면 WAITING 전이가 절대 일어나서는 안 된다
    @Test
    void sendDisputeWarningsAndStartWaiting_whenAnyVerifiedContactFails_doesNotStartWaitingAndFreezes() {
        DisputeContact ok = mock(DisputeContact.class);
        when(ok.getIsVerified()).thenReturn(true);
        when(ok.getPlan()).thenReturn(plan);
        when(ok.getEmail()).thenReturn("ok@test.com");
        DisputeContact failing = mock(DisputeContact.class);
        when(failing.getIsVerified()).thenReturn(true);
        when(failing.getPlan()).thenReturn(plan);
        when(failing.getEmail()).thenReturn("bad@test.com");
        when(disputeContactRepository.findByPlan_PlanId(PLAN_ID)).thenReturn(List.of(ok, failing));
        when(criticalEmailSender.sendOrFail(eq(plan), eq(EmailType.OBJECTION_WINDOW_NOTICE), eq("ok@test.com"), any()))
                .thenReturn(true);
        when(criticalEmailSender.sendOrFail(eq(plan), eq(EmailType.OBJECTION_WINDOW_NOTICE), eq("bad@test.com"), any()))
                .thenReturn(false);

        boolean result = releaseCaseWarningService.sendDisputeWarningsAndStartWaiting(releaseCase, 7);

        assertThat(result).isFalse();
        verify(releaseCase, never()).approveEvidenceAndStartWaiting(any());
        verify(releaseCase).freeze();
        // 성공한 연락처에게는 계속 시도해 실제로 발송한다 - 한 명 실패했다고 나머지까지 건너뛰지 않는다
        verify(criticalEmailSender).sendOrFail(eq(plan), eq(EmailType.OBJECTION_WINDOW_NOTICE), eq("ok@test.com"), any());
    }

    @Test
    void sendDisputeWarningsAndStartWaiting_ignoresUnverifiedContacts() {
        DisputeContact unverified = mock(DisputeContact.class);
        when(unverified.getIsVerified()).thenReturn(false);
        when(disputeContactRepository.findByPlan_PlanId(PLAN_ID)).thenReturn(List.of(unverified));

        boolean result = releaseCaseWarningService.sendDisputeWarningsAndStartWaiting(releaseCase, 7);

        assertThat(result).isTrue();
        verify(releaseCase).approveEvidenceAndStartWaiting(7);
        verify(criticalEmailSender, never()).sendOrFail(any(), any(), anyString(), any());
    }
}
