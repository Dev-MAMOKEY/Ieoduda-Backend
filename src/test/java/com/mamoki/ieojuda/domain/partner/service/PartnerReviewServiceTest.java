package com.mamoki.ieojuda.domain.partner.service;

import com.mamoki.ieojuda.domain.account.entity.AdminPermission;
import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.audit.entity.AdminActionType;
import com.mamoki.ieojuda.domain.audit.service.AdminActionAuditService;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.service.DisputeContactService;
import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import com.mamoki.ieojuda.domain.evidence.entity.EvidenceReviewStatus;
import com.mamoki.ieojuda.domain.evidence.entity.EvidenceType;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceRepository;
import com.mamoki.ieojuda.domain.partner.dto.PartnerReviewDecisionRequest;
import com.mamoki.ieojuda.domain.partner.dto.PartnerReviewListItemResponse;
import com.mamoki.ieojuda.domain.partner.entity.PartnerReviewer;
import com.mamoki.ieojuda.domain.partner.repository.PartnerReviewerRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
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
class PartnerReviewServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID REVIEW_ID = UUID.randomUUID();

    private EvidenceRepository evidenceRepository;
    private PartnerReviewerRepository partnerReviewerRepository;
    private EvidenceStorageClient evidenceStorageClient;
    private PermissionGuard permissionGuard;
    private ReauthGuard reauthGuard;
    private AdminActionAuditService adminActionAuditService;
    private IdempotencyGuard idempotencyGuard;
    private SecurityTokenService securityTokenService;
    private DisputeContactService disputeContactService;
    private PartnerReviewService partnerReviewService;

    private User actor;
    private Evidence evidence;
    private ReleaseCase releaseCase;
    private PartnerReviewer reviewer;

    @BeforeEach
    void setUp() {
        evidenceRepository = mock(EvidenceRepository.class);
        partnerReviewerRepository = mock(PartnerReviewerRepository.class);
        evidenceStorageClient = mock(EvidenceStorageClient.class);
        permissionGuard = mock(PermissionGuard.class);
        reauthGuard = mock(ReauthGuard.class);
        adminActionAuditService = mock(AdminActionAuditService.class);
        idempotencyGuard = mock(IdempotencyGuard.class);
        securityTokenService = mock(SecurityTokenService.class);
        disputeContactService = mock(DisputeContactService.class);
        partnerReviewService = new PartnerReviewService(
                evidenceRepository, partnerReviewerRepository, evidenceStorageClient,
                permissionGuard, reauthGuard, adminActionAuditService, idempotencyGuard,
                securityTokenService, disputeContactService);

        actor = mock(User.class);
        when(permissionGuard.require(USER_ID, AdminPermission.EVIDENCE_REVIEW)).thenReturn(actor);

        reviewer = mock(PartnerReviewer.class);
        when(reviewer.getIsActive()).thenReturn(true);
        when(partnerReviewerRepository.findByUser_UserId(USER_ID)).thenReturn(Optional.of(reviewer));

        releaseCase = mock(ReleaseCase.class);
        Plan plan = mock(Plan.class);
        User planOwner = mock(User.class);
        when(planOwner.getName()).thenReturn("작성자");
        when(plan.getUser()).thenReturn(planOwner);
        Confirmer confirmer = mock(Confirmer.class);
        when(confirmer.getName()).thenReturn("확인자");
        evidence = mock(Evidence.class);
        when(evidence.getReleaseCase()).thenReturn(releaseCase);
        when(evidence.getPlan()).thenReturn(plan);
        when(evidence.getConfirmer()).thenReturn(confirmer);
        when(evidence.getReviewStatus()).thenReturn(EvidenceReviewStatus.PENDING);
        when(evidence.getEvidenceType()).thenReturn(EvidenceType.DEATH_CERTIFICATE);
        when(evidenceRepository.findById(REVIEW_ID)).thenReturn(Optional.of(evidence));
    }

    @Test
    void decide_proceedsWithoutAnyPartnerAssignment() {
        // 배정/소속 개념이 없으므로 releaseCase에 아무 배정 정보가 없어도 정상 처리되어야 한다.
        PartnerReviewDecisionRequest request = new PartnerReviewDecisionRequest(
                PartnerReviewDecisionRequest.PartnerReviewDecision.APPROVE, null, "correct-pw");

        partnerReviewService.decide(REVIEW_ID, USER_ID, request, null);

        verify(evidence).approve();
        verify(releaseCase).approveEvidenceAndStartWaiting(any());
        verify(adminActionAuditService).record(actor, AdminActionType.EVIDENCE_DECISION, REVIEW_ID, true, "APPROVE");
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
    void getFile_whenEvidenceAlreadyDeleted_isBlocked() {
        when(evidence.getDeletedAt()).thenReturn(LocalDateTime.now());

        assertThatThrownBy(() -> partnerReviewService.getFile(USER_ID, REVIEW_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVIDENCE_ALREADY_DELETED));
        verify(evidenceStorageClient, never()).load(any());
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
