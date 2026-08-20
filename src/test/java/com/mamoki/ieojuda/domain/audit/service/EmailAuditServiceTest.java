package com.mamoki.ieojuda.domain.audit.service;

import com.mamoki.ieojuda.domain.account.entity.AdminPermission;
import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.audit.entity.AdminActionType;
import com.mamoki.ieojuda.domain.audit.repository.EmailLogRepository;
import com.mamoki.ieojuda.domain.confirmer.entity.ReportStatus;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.evidence.entity.EvidenceReviewStatus;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCaseStatus;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.releasecase.service.ReleaseCaseWarningService;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxService;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.security.PermissionGuard;
import com.mamoki.ieojuda.global.security.ReauthGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// "경고 발송 재시도"가 재인증 없이는 실행되지 않고, 이미 WAITING 이후로 넘어간 사건은 재시도 대상에서
// 제외되며, 재시도 성공 시에만 동결이 풀리는지 검증한다.
class EmailAuditServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CASE_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();

    private ReleaseCaseRepository releaseCaseRepository;
    private EmailLogRepository emailLogRepository;
    private EmailOutboxService emailOutboxService;
    private AppProperties appProperties;
    private PermissionGuard permissionGuard;
    private ReauthGuard reauthGuard;
    private AdminActionAuditService adminActionAuditService;
    private SecurityTokenService securityTokenService;
    private ReleaseCaseWarningService releaseCaseWarningService;
    private EvidenceRepository evidenceRepository;
    private ConfirmerRepository confirmerRepository;
    private EmailAuditService emailAuditService;

    private User actor;
    private ReleaseCase releaseCase;
    private Plan plan;

    @BeforeEach
    void setUp() {
        releaseCaseRepository = mock(ReleaseCaseRepository.class);
        emailLogRepository = mock(EmailLogRepository.class);
        emailOutboxService = mock(EmailOutboxService.class);
        appProperties = mock(AppProperties.class);
        permissionGuard = mock(PermissionGuard.class);
        reauthGuard = mock(ReauthGuard.class);
        adminActionAuditService = mock(AdminActionAuditService.class);
        securityTokenService = mock(SecurityTokenService.class);
        releaseCaseWarningService = mock(ReleaseCaseWarningService.class);
        evidenceRepository = mock(EvidenceRepository.class);
        confirmerRepository = mock(ConfirmerRepository.class);
        emailAuditService = new EmailAuditService(
                releaseCaseRepository, emailLogRepository, emailOutboxService, appProperties, permissionGuard,
                reauthGuard, adminActionAuditService, securityTokenService, releaseCaseWarningService,
                evidenceRepository, confirmerRepository);

        actor = mock(User.class);
        when(permissionGuard.require(USER_ID, AdminPermission.CASE_SUPERVISE)).thenReturn(actor);

        plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(PLAN_ID);
        when(plan.getWaitingDays()).thenReturn(7);
        releaseCase = mock(ReleaseCase.class);
        when(releaseCase.getPlan()).thenReturn(plan);
        when(releaseCaseRepository.findById(CASE_ID)).thenReturn(Optional.of(releaseCase));
    }

    @Test
    void retryWarning_whenReauthFails_doesNotAttemptRetry() {
        doThrow(new CustomException(ErrorCode.REAUTH_FAILED)).when(reauthGuard).verify(actor, "wrong-pw");

        assertThatThrownBy(() -> emailAuditService.retryWarning(USER_ID, CASE_ID, "wrong-pw"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REAUTH_FAILED));

        verify(releaseCaseWarningService, never()).sendAuthorCancelWarningOrFreeze(any());
        verify(releaseCaseWarningService, never()).sendDisputeWarningsAndStartWaiting(any(), any());
    }

    @Test
    void retryWarning_whenCaseAlreadyWaiting_isRejected() {
        when(releaseCase.getStatus()).thenReturn(ReleaseCaseStatus.WAITING);

        assertThatThrownBy(() -> emailAuditService.retryWarning(USER_ID, CASE_ID, "pw"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));

        verify(releaseCaseWarningService, never()).sendAuthorCancelWarningOrFreeze(any());
        verify(releaseCaseWarningService, never()).sendDisputeWarningsAndStartWaiting(any(), any());
    }

    // 증빙이 아직 전부 승인되지 않은 사건 - 사건 생성 시점의 작성자 경고가 막혀 있던 경우
    @Test
    void retryWarning_whenEvidenceNotFullyApproved_retriesAuthorWarningAndUnfreezesOnSuccess() {
        when(releaseCase.getStatus()).thenReturn(ReleaseCaseStatus.EVIDENCE_PENDING);
        when(evidenceRepository.countByReleaseCase_CaseIdAndReviewStatus(CASE_ID, EvidenceReviewStatus.APPROVED)).thenReturn(0L);
        when(confirmerRepository.findByPlan_PlanIdAndReportStatus(PLAN_ID, ReportStatus.MATCHED)).thenReturn(List.of());
        when(releaseCaseWarningService.sendAuthorCancelWarningOrFreeze(releaseCase)).thenReturn(true);

        emailAuditService.retryWarning(USER_ID, CASE_ID, "pw");

        verify(releaseCaseWarningService).sendAuthorCancelWarningOrFreeze(releaseCase);
        verify(releaseCaseWarningService, never()).sendDisputeWarningsAndStartWaiting(any(), any());
        verify(releaseCase).unfreeze();
        verify(adminActionAuditService).record(actor, AdminActionType.CASE_WARNING_RETRY, CASE_ID, true, null);
    }

    // 증빙이 전부 승인된 사건 - 대기 시작 시점의 이의 연락처 경고가 막혀 있던 경우
    @Test
    void retryWarning_whenEvidenceFullyApproved_retriesDisputeWarningAndStaysFrozenOnFailure() {
        when(releaseCase.getStatus()).thenReturn(ReleaseCaseStatus.EVIDENCE_APPROVED);
        when(evidenceRepository.countByReleaseCase_CaseIdAndReviewStatus(CASE_ID, EvidenceReviewStatus.APPROVED)).thenReturn(2L);
        when(confirmerRepository.findByPlan_PlanIdAndReportStatus(PLAN_ID, ReportStatus.MATCHED))
                .thenReturn(List.of(mock(com.mamoki.ieojuda.domain.confirmer.entity.Confirmer.class),
                        mock(com.mamoki.ieojuda.domain.confirmer.entity.Confirmer.class)));
        when(releaseCaseWarningService.sendDisputeWarningsAndStartWaiting(releaseCase, 7)).thenReturn(false);

        emailAuditService.retryWarning(USER_ID, CASE_ID, "pw");

        verify(releaseCaseWarningService).sendDisputeWarningsAndStartWaiting(releaseCase, 7);
        verify(releaseCase, never()).unfreeze();
        verify(adminActionAuditService).record(actor, AdminActionType.CASE_WARNING_RETRY, CASE_ID, false, "발송 재시도 실패");
    }

    @Test
    void unfreeze_whenReauthSucceeds_clearsFrozenFlag() {
        emailAuditService.unfreeze(USER_ID, CASE_ID, "pw");

        verify(releaseCase).unfreeze();
        verify(adminActionAuditService).record(actor, AdminActionType.CASE_UNFREEZE, CASE_ID, true, null);
    }
}
