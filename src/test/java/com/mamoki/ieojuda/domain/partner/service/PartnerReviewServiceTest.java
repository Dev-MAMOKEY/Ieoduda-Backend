package com.mamoki.ieojuda.domain.partner.service;

import com.mamoki.ieojuda.domain.account.entity.AdminPermission;
import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.audit.entity.AdminActionType;
import com.mamoki.ieojuda.domain.audit.service.AdminActionAuditService;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import com.mamoki.ieojuda.domain.evidence.entity.EvidenceDownloadToken;
import com.mamoki.ieojuda.domain.evidence.entity.EvidenceReviewStatus;
import com.mamoki.ieojuda.domain.evidence.entity.EvidenceType;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceDownloadTokenRepository;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceRepository;
import com.mamoki.ieojuda.domain.partner.dto.EvidenceDownloadLinkResponse;
import com.mamoki.ieojuda.domain.partner.dto.PartnerReviewDecisionRequest;
import com.mamoki.ieojuda.domain.partner.dto.PartnerReviewListItemResponse;
import com.mamoki.ieojuda.domain.partner.entity.PartnerReviewer;
import com.mamoki.ieojuda.domain.partner.repository.PartnerReviewerRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.service.ReleaseCaseWarningService;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.idempotency.service.IdempotencyGuard;
import com.mamoki.ieojuda.global.security.PermissionGuard;
import com.mamoki.ieojuda.global.security.ReauthGuard;
import com.mamoki.ieojuda.global.storage.EvidenceStorageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// issue #59 회귀 테스트 - 증빙 판정은 재인증 없이는 실행되지 않아야 한다.
// 배정/소속(조직 경계) 개념은 폐지되어 EVIDENCE_REVIEW 권한만 있으면 모든 사건·증빙을 조작할 수 있다.
// issue #43 - 판정은 한 번 완료되면 다시 바꿀 수 없고, 원본은 1회성 토큰으로만 내려받을 수 있어야 한다.
class PartnerReviewServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID REVIEW_ID = UUID.randomUUID();

    private EvidenceRepository evidenceRepository;
    private ConfirmerRepository confirmerRepository;
    private PartnerReviewerRepository partnerReviewerRepository;
    private EvidenceDownloadTokenRepository evidenceDownloadTokenRepository;
    private EvidenceStorageClient evidenceStorageClient;
    private PermissionGuard permissionGuard;
    private ReauthGuard reauthGuard;
    private AdminActionAuditService adminActionAuditService;
    private IdempotencyGuard idempotencyGuard;
    private SecurityTokenService securityTokenService;
    private ReleaseCaseWarningService releaseCaseWarningService;
    private PartnerReviewService partnerReviewService;

    private User actor;
    private Evidence evidence;
    private ReleaseCase releaseCase;
    private PartnerReviewer reviewer;

    @BeforeEach
    void setUp() {
        evidenceRepository = mock(EvidenceRepository.class);
        confirmerRepository = mock(ConfirmerRepository.class);
        partnerReviewerRepository = mock(PartnerReviewerRepository.class);
        evidenceDownloadTokenRepository = mock(EvidenceDownloadTokenRepository.class);
        evidenceStorageClient = mock(EvidenceStorageClient.class);
        permissionGuard = mock(PermissionGuard.class);
        reauthGuard = mock(ReauthGuard.class);
        adminActionAuditService = mock(AdminActionAuditService.class);
        idempotencyGuard = mock(IdempotencyGuard.class);
        securityTokenService = mock(SecurityTokenService.class);
        releaseCaseWarningService = mock(ReleaseCaseWarningService.class);
        partnerReviewService = new PartnerReviewService(
                evidenceRepository, confirmerRepository, partnerReviewerRepository, evidenceDownloadTokenRepository,
                evidenceStorageClient, permissionGuard, reauthGuard, adminActionAuditService, idempotencyGuard,
                securityTokenService, releaseCaseWarningService);

        actor = mock(User.class);
        when(permissionGuard.require(USER_ID, AdminPermission.EVIDENCE_REVIEW)).thenReturn(actor);

        reviewer = mock(PartnerReviewer.class);
        when(reviewer.getReviewerId()).thenReturn(UUID.randomUUID());
        when(reviewer.getIsActive()).thenReturn(true);
        when(partnerReviewerRepository.findByUser_UserId(USER_ID)).thenReturn(Optional.of(reviewer));

        releaseCase = mock(ReleaseCase.class);
        // issue #45 - 다중 증빙 승인 정책이 사건별로 승인 건수를 집계하는 데 필요하다
        when(releaseCase.getCaseId()).thenReturn(UUID.randomUUID());
        Plan plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(UUID.randomUUID());
        when(plan.getWaitingDays()).thenReturn(7);
        User planOwner = mock(User.class);
        when(planOwner.getName()).thenReturn("작성자");
        when(plan.getUser()).thenReturn(planOwner);
        when(releaseCase.getPlan()).thenReturn(plan);
        Confirmer confirmer = mock(Confirmer.class);
        when(confirmer.getName()).thenReturn("확인자");
        evidence = mock(Evidence.class);
        when(evidence.getEvidenceId()).thenReturn(REVIEW_ID);
        when(evidence.getReleaseCase()).thenReturn(releaseCase);
        when(evidence.getPlan()).thenReturn(plan);
        when(evidence.getConfirmer()).thenReturn(confirmer);
        when(evidence.getReviewStatus()).thenReturn(EvidenceReviewStatus.PENDING);
        when(evidence.getEvidenceType()).thenReturn(EvidenceType.DEATH_CERTIFICATE);
        when(evidenceRepository.findById(REVIEW_ID)).thenReturn(Optional.of(evidence));
        // 기본값: 매칭된 확인자가 1명뿐인 사건 - 승인 1건만으로 정족수를 채워 바로 WAITING까지 진행된다
        // (다중 확인자 정족수 시나리오는 별도 테스트에서 개별적으로 재정의한다)
        when(confirmerRepository.findByPlan_PlanIdAndReportStatus(any(), any()))
                .thenReturn(List.of(mock(Confirmer.class)));
        when(evidenceRepository.countByReleaseCase_CaseIdAndReviewStatus(any(), any())).thenReturn(0L);
    }

    @Test
    void decide_proceedsWithoutAnyPartnerAssignment() {
        // 배정/소속 개념이 없으므로 releaseCase에 아무 배정 정보가 없어도 정상 처리되어야 한다.
        PartnerReviewDecisionRequest request = new PartnerReviewDecisionRequest(
                PartnerReviewDecisionRequest.PartnerReviewDecision.APPROVE, null, "correct-pw");

        partnerReviewService.decide(REVIEW_ID, USER_ID, request, null);

        verify(evidence).approve();
        // WAITING 전이는 이제 ReleaseCaseWarningService가 경고 발송 성공을 확인한 뒤에만 수행한다
        verify(releaseCaseWarningService).sendDisputeWarningsAndStartWaiting(releaseCase, 7);
        verify(adminActionAuditService).record(actor, AdminActionType.EVIDENCE_DECISION, REVIEW_ID, true, "APPROVE");
        // issue #43 - 개별 사전 배정은 없지만, 실제로 판정한 사람은 표시·감사용으로 기록해둔다
        verify(evidence).assignReviewer(reviewer);
    }

    // issue #45 - "여러 증빙의 승인 정책": 매칭된 확인자가 2명인 사건에서 아직 1건만 승인됐다면
    // WAITING으로 넘기지 않고 부분 승인(EVIDENCE_APPROVED) 상태로만 남겨야 한다.
    @Test
    void decide_whenOnlyOneOfTwoMatchedConfirmersApproved_leavesCasePartiallyApproved() {
        when(confirmerRepository.findByPlan_PlanIdAndReportStatus(any(), any()))
                .thenReturn(List.of(mock(Confirmer.class), mock(Confirmer.class)));
        when(evidenceRepository.countByReleaseCase_CaseIdAndReviewStatus(any(), any())).thenReturn(0L);
        PartnerReviewDecisionRequest request = new PartnerReviewDecisionRequest(
                PartnerReviewDecisionRequest.PartnerReviewDecision.APPROVE, null, "correct-pw");

        partnerReviewService.decide(REVIEW_ID, USER_ID, request, null);

        verify(evidence).approve();
        verify(releaseCase).markEvidencePartiallyApproved();
        verify(releaseCaseWarningService, never()).sendDisputeWarningsAndStartWaiting(any(), any());
    }

    // 매칭된 두 확인자 중 이미 한 명이 승인된 상태에서 나머지 한 명의 증빙까지 승인되면
    // 그때 비로소 WAITING으로 전환돼야 한다.
    @Test
    void decide_whenSecondOfTwoMatchedConfirmersApproved_startsWaiting() {
        when(confirmerRepository.findByPlan_PlanIdAndReportStatus(any(), any()))
                .thenReturn(List.of(mock(Confirmer.class), mock(Confirmer.class)));
        when(evidenceRepository.countByReleaseCase_CaseIdAndReviewStatus(any(), any())).thenReturn(1L);
        PartnerReviewDecisionRequest request = new PartnerReviewDecisionRequest(
                PartnerReviewDecisionRequest.PartnerReviewDecision.APPROVE, null, "correct-pw");

        partnerReviewService.decide(REVIEW_ID, USER_ID, request, null);

        verify(evidence).approve();
        verify(releaseCaseWarningService).sendDisputeWarningsAndStartWaiting(releaseCase, 7);
        verify(releaseCase, never()).markEvidencePartiallyApproved();
    }

    @Test
    void decide_whenReviewerInactive_isBlockedByConflictOfInterest() {
        when(reviewer.getIsActive()).thenReturn(false);
        PartnerReviewDecisionRequest request = new PartnerReviewDecisionRequest(
                PartnerReviewDecisionRequest.PartnerReviewDecision.APPROVE, null, "pw");

        assertThatThrownBy(() -> partnerReviewService.decide(REVIEW_ID, USER_ID, request, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REVIEWER_CONFLICT_OF_INTEREST));
        verify(evidence, never()).approve();
    }

    // issue #43 완료 조건 - 판정을 1회 상태 전이로 제한한다
    @Test
    void decide_whenAlreadyApproved_isBlockedFromDecidingAgain() {
        when(evidence.getReviewStatus()).thenReturn(EvidenceReviewStatus.APPROVED);
        PartnerReviewDecisionRequest request = new PartnerReviewDecisionRequest(
                PartnerReviewDecisionRequest.PartnerReviewDecision.REJECT, "사유", "pw");

        assertThatThrownBy(() -> partnerReviewService.decide(REVIEW_ID, USER_ID, request, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVIDENCE_ALREADY_DECIDED));
        verify(evidence, never()).reject(any());
    }

    @Test
    void decide_whenAlreadyRejected_isBlockedFromDecidingAgain() {
        when(evidence.getReviewStatus()).thenReturn(EvidenceReviewStatus.REJECTED);
        PartnerReviewDecisionRequest request = new PartnerReviewDecisionRequest(
                PartnerReviewDecisionRequest.PartnerReviewDecision.APPROVE, null, "pw");

        assertThatThrownBy(() -> partnerReviewService.decide(REVIEW_ID, USER_ID, request, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVIDENCE_ALREADY_DECIDED));
        verify(evidence, never()).approve();
    }

    // 추가자료요청은 종결 상태가 아니므로, 그 이후에 다시 판정할 수 있어야 한다
    @Test
    void decide_whenAdditionalInfoRequested_canStillBeDecidedAgain() {
        when(evidence.getReviewStatus()).thenReturn(EvidenceReviewStatus.ADDITIONAL_INFO_REQUESTED);
        PartnerReviewDecisionRequest request = new PartnerReviewDecisionRequest(
                PartnerReviewDecisionRequest.PartnerReviewDecision.APPROVE, null, "correct-pw");

        partnerReviewService.decide(REVIEW_ID, USER_ID, request, null);

        verify(evidence).approve();
    }

    @Test
    void decide_whenReauthFails_blocksTheDecisionAndRecordsTheFailure() {
        doThrow(new CustomException(ErrorCode.REAUTH_FAILED))
                .when(reauthGuard).verify(actor, "wrong-pw");
        PartnerReviewDecisionRequest request = new PartnerReviewDecisionRequest(
                PartnerReviewDecisionRequest.PartnerReviewDecision.APPROVE, null, "wrong-pw");

        assertThatThrownBy(() -> partnerReviewService.decide(REVIEW_ID, USER_ID, request, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REAUTH_FAILED));

        verify(evidence, never()).approve();
        verify(adminActionAuditService).record(actor, AdminActionType.EVIDENCE_DECISION, REVIEW_ID, false, "재인증 실패");
    }

    @Test
    void decide_whenUserLacksEvidenceReviewPermission_isBlockedBeforeAnyLookup() {
        when(permissionGuard.require(USER_ID, AdminPermission.EVIDENCE_REVIEW))
                .thenThrow(new CustomException(ErrorCode.INSUFFICIENT_PERMISSION));
        PartnerReviewDecisionRequest request = new PartnerReviewDecisionRequest(
                PartnerReviewDecisionRequest.PartnerReviewDecision.APPROVE, null, "pw");

        assertThatThrownBy(() -> partnerReviewService.decide(REVIEW_ID, USER_ID, request, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_PERMISSION));
        verify(evidenceRepository, never()).findById(any());
    }

    // 원본이 이미 삭제된 증빙은 세 경로(조회/다운로드/판정) 모두 차단되어야 한다 -
    // 특히 판정을 막지 못하면 존재하지 않는 파일을 승인/반려하는 정합성 결함이 생긴다.
    @Test
    void getReview_whenEvidenceAlreadyDeleted_isBlocked() {
        when(evidence.getDeletedAt()).thenReturn(LocalDateTime.now());

        assertThatThrownBy(() -> partnerReviewService.getReview(USER_ID, REVIEW_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVIDENCE_ALREADY_DELETED));
    }

    @Test
    void requestDownloadLink_whenEvidenceAlreadyDeleted_isBlocked() {
        when(evidence.getDeletedAt()).thenReturn(LocalDateTime.now());

        assertThatThrownBy(() -> partnerReviewService.requestDownloadLink(USER_ID, REVIEW_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVIDENCE_ALREADY_DELETED));
    }

    @Test
    void decide_whenEvidenceAlreadyDeleted_isBlocked() {
        when(evidence.getDeletedAt()).thenReturn(LocalDateTime.now());
        PartnerReviewDecisionRequest request = new PartnerReviewDecisionRequest(
                PartnerReviewDecisionRequest.PartnerReviewDecision.APPROVE, null, "pw");

        assertThatThrownBy(() -> partnerReviewService.decide(REVIEW_ID, USER_ID, request, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVIDENCE_ALREADY_DELETED));
        verify(evidence, never()).approve();
    }

    // issue #43 - 원본은 발급받은 1회성 토큰으로만 내려받을 수 있고, 같은 토큰을 두 번 쓸 수 없다
    @Test
    void requestDownloadLink_issuesDownloadToken() {
        EvidenceDownloadLinkResponse response = partnerReviewService.requestDownloadLink(USER_ID, REVIEW_ID);

        assertThat(response.downloadToken()).isNotBlank();
        assertThat(response.expiresAt()).isAfter(LocalDateTime.now());
        verify(evidenceDownloadTokenRepository).save(any(EvidenceDownloadToken.class));
    }

    @Test
    void downloadFile_whenTokenUnknown_throwsTokenInvalid() {
        when(evidenceDownloadTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partnerReviewService.downloadFile(USER_ID, REVIEW_ID, "unknown-token"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID));
        verify(evidenceStorageClient, never()).load(any());
    }

    @Test
    void downloadFile_whenTokenExpired_throwsAccessLinkExpired() {
        EvidenceDownloadToken token = mock(EvidenceDownloadToken.class);
        when(token.getEvidence()).thenReturn(evidence);
        when(token.isExpired()).thenReturn(true);
        when(evidenceDownloadTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> partnerReviewService.downloadFile(USER_ID, REVIEW_ID, "expired-token"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_LINK_EXPIRED));
        verify(evidenceStorageClient, never()).load(any());
    }

    @Test
    void downloadFile_whenTokenAlreadyUsed_throwsAccessLinkAlreadyUsed() {
        EvidenceDownloadToken token = mock(EvidenceDownloadToken.class);
        when(token.getEvidence()).thenReturn(evidence);
        when(token.isExpired()).thenReturn(false);
        when(evidenceDownloadTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(evidenceDownloadTokenRepository.markUsedIfUnused(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> partnerReviewService.downloadFile(USER_ID, REVIEW_ID, "used-token"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_LINK_ALREADY_USED));
        verify(evidenceStorageClient, never()).load(any());
    }

    @Test
    void downloadFile_whenTokenValid_loadsFileAndRecordsAuditByActor() {
        EvidenceDownloadToken token = mock(EvidenceDownloadToken.class);
        when(token.getEvidence()).thenReturn(evidence);
        when(token.isExpired()).thenReturn(false);
        when(evidenceDownloadTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(evidenceDownloadTokenRepository.markUsedIfUnused(any(), any())).thenReturn(1);
        when(evidence.getStorageKey()).thenReturn("storage/key");
        byte[] bytes = {1, 2, 3};
        when(evidenceStorageClient.load("storage/key")).thenReturn(bytes);

        byte[] result = partnerReviewService.downloadFile(USER_ID, REVIEW_ID, "valid-token");

        assertThat(result).isEqualTo(bytes);
        verify(adminActionAuditService).record(actor, AdminActionType.EVIDENCE_DOWNLOAD, REVIEW_ID, true, null);
    }

    // issue #87 - EVIDENCE_REVIEW 권한만 있으면 전체가 조회되어야 한다는 요구사항
    @Test
    void getReviews_returnsAllEvidences() {
        when(evidence.getEvidenceId()).thenReturn(REVIEW_ID);
        when(evidenceRepository.findAllByReviewStatus(EvidenceReviewStatus.PENDING))
                .thenReturn(List.of(evidence));

        List<PartnerReviewListItemResponse> result = partnerReviewService.getReviews(USER_ID, EvidenceReviewStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).reviewId()).isEqualTo(REVIEW_ID);
    }

    @Test
    void getReviews_passesNullStatusThrough_whenNoFilterGiven() {
        when(evidenceRepository.findAllByReviewStatus(isNull())).thenReturn(List.of());

        partnerReviewService.getReviews(USER_ID, null);

        verify(evidenceRepository).findAllByReviewStatus(isNull());
    }

    @Test
    void getReviews_whenUserLacksEvidenceReviewPermission_isBlockedBeforeAnyLookup() {
        when(permissionGuard.require(USER_ID, AdminPermission.EVIDENCE_REVIEW))
                .thenThrow(new CustomException(ErrorCode.INSUFFICIENT_PERMISSION));

        assertThatThrownBy(() -> partnerReviewService.getReviews(USER_ID, EvidenceReviewStatus.PENDING))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_PERMISSION));
        verify(evidenceRepository, never()).findAllByReviewStatus(any());
    }
}
