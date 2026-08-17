package com.mamoki.ieojuda.domain.partner.service;

import java.util.UUID;

import com.mamoki.ieojuda.domain.account.entity.AdminPermission;
import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.audit.entity.AdminActionType;
import com.mamoki.ieojuda.domain.audit.service.AdminActionAuditService;
import com.mamoki.ieojuda.domain.confirmer.service.DisputeContactService;
import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceRepository;
import com.mamoki.ieojuda.domain.partner.dto.PartnerReviewDecisionRequest;
import com.mamoki.ieojuda.domain.partner.dto.PartnerReviewResponse;
import com.mamoki.ieojuda.domain.partner.entity.PartnerReviewer;
import com.mamoki.ieojuda.domain.partner.repository.PartnerReviewerRepository;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.idempotency.service.IdempotencyGuard;
import com.mamoki.ieojuda.global.security.PermissionGuard;
import com.mamoki.ieojuda.global.security.ReauthGuard;
import com.mamoki.ieojuda.global.storage.EvidenceStorageClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 명세서 "외부 파트너 증빙 검토" 화면 - 외부 법무·장례 파트너 전용. 역할별 패키지(계획 내용)는 절대 노출하지 않는다.
// reviewId는 evidenceId와 1:1로 취급한다(증빙 1건 = 검토 1건).
// issue #59 - EVIDENCE_REVIEW 세부 권한 + 소속 파트너사가 배정된 사건만 조작 가능(조직 경계) + 판정은 재인증 필요
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnerReviewService {

    private final EvidenceRepository evidenceRepository;
    private final PartnerReviewerRepository partnerReviewerRepository;
    private final EvidenceStorageClient evidenceStorageClient;
    private final PermissionGuard permissionGuard;
    private final ReauthGuard reauthGuard;
    private final AdminActionAuditService adminActionAuditService;
    private final IdempotencyGuard idempotencyGuard;
    private final SecurityTokenService securityTokenService;
    private final DisputeContactService disputeContactService;

    public PartnerReviewResponse getReview(UUID userId, UUID reviewId) {
        permissionGuard.require(userId, AdminPermission.EVIDENCE_REVIEW);
        Evidence evidence = findEvidence(reviewId);
        requireAssignedPartner(findReviewerByUser(userId), evidence);
        return PartnerReviewResponse.from(evidence);
    }

    // 증빙 원본 다운로드 - JSON 응답에 바이너리를 섞지 않기 위해 별도 엔드포인트로 분리
    public byte[] getFile(UUID userId, UUID reviewId) {
        permissionGuard.require(userId, AdminPermission.EVIDENCE_REVIEW);
        Evidence evidence = findEvidence(reviewId);
        requireAssignedPartner(findReviewerByUser(userId), evidence);
        return evidenceStorageClient.load(evidence.getStorageKey());
    }

    @Transactional
    public PartnerReviewResponse decide(UUID reviewId, UUID userId, PartnerReviewDecisionRequest request, String idempotencyKey) {
        User actor = permissionGuard.require(userId, AdminPermission.EVIDENCE_REVIEW);
        Evidence evidence = findEvidence(reviewId);
        PartnerReviewer reviewer = findReviewerByUser(userId);

        // 이해충돌·권한 만료로 비활성화된 검토자는 결정할 수 없음 (명세서 "다른 검토자에게 재배정")
        if (!Boolean.TRUE.equals(reviewer.getIsActive())) {
            throw new CustomException(ErrorCode.REVIEWER_CONFLICT_OF_INTEREST);
        }
        requireAssignedPartner(reviewer, evidence);

        // 되돌리기 까다로운 고위험 조작이라 비밀번호 재확인 없이는 실행하지 않고, 성공/실패를 감사 로그에 남긴다.
        try {
            reauthGuard.verify(actor, request.password());
        } catch (CustomException e) {
            adminActionAuditService.record(actor, AdminActionType.EVIDENCE_DECISION, reviewId, false, "재인증 실패");
            throw e;
        }
        // 재인증 성공 이후에 클레임해야, 재인증 실패로 아무 것도 안 바뀐 시도가 키를 낭비해서
        // 정상적인 재시도까지 막는 일이 없다.
        idempotencyGuard.claim("partner-review-decision", idempotencyKey);

        evidence.assignReviewer(reviewer);

        switch (request.decision()) {
            // 승인되면 계획에 설정된 대기 기간만큼 발송을 미루는 대기 상태로 사건을 전이시킨다
            case APPROVE -> {
                evidence.approve();
                ReleaseCase releaseCase = evidence.getReleaseCase();
                releaseCase.approveEvidenceAndStartWaiting(evidence.getPlan().getWaitingDays());
                // issue #41 - 증빙 제출 단계가 끝났으므로 UPLOAD_EVIDENCE 토큰은 더 이상 필요 없다
                securityTokenService.revokeAllForCase(releaseCase, SecurityTokenPurpose.UPLOAD_EVIDENCE);
                disputeContactService.notifyVerifiedContactsOfObjectionWindow(releaseCase);
            }
            case REJECT -> {
                if (request.failureReason() == null || request.failureReason().isBlank()) {
                    throw new CustomException(ErrorCode.INVALID_INPUT);
                }
                evidence.reject(request.failureReason());
                ReleaseCase releaseCase = evidence.getReleaseCase();
                releaseCase.rejectEvidence();
                securityTokenService.revokeAllForCase(releaseCase, SecurityTokenPurpose.UPLOAD_EVIDENCE);
            }
            case ADDITIONAL_INFO_REQUESTED -> evidence.reAdditionalInfo();
        }

        adminActionAuditService.record(actor, AdminActionType.EVIDENCE_DECISION, reviewId, true,
                request.decision().name());
        return PartnerReviewResponse.from(evidence);
    }

    // issue #59 - 검토자 소속 파트너사와 사건에 배정된 파트너사가 같아야만 조작 가능. 아직 배정되지 않은
    // 사건은 어떤 검토자도 손댈 수 없다(운영자 배정이 먼저 있어야 함).
    private void requireAssignedPartner(PartnerReviewer reviewer, Evidence evidence) {
        ReleaseCase releaseCase = evidence.getReleaseCase();
        if (releaseCase.getAssignedPartner() == null) {
            throw new CustomException(ErrorCode.PARTNER_NOT_ASSIGNED);
        }
        if (!releaseCase.getAssignedPartner().getPartnerId().equals(reviewer.getPartner().getPartnerId())) {
            throw new CustomException(ErrorCode.PARTNER_SCOPE_DENIED);
        }
    }

    // getReview/getFile/decide 세 경로가 모두 이 메서드를 거치므로, 원본이 이미 삭제된 증빙을
    // 여기서 한 번에 막는다. 원본 삭제 후 승인/반려를 허용하면 존재하지 않는 파일을 판정하는
    // 정합성 결함이 생기고, 다운로드는 지금까지 S3까지 갔다가 404로 새고 있었다.
    private Evidence findEvidence(UUID reviewId) {
        Evidence evidence = evidenceRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.EVIDENCE_NOT_FOUND));
        if (evidence.getDeletedAt() != null) {
            throw new CustomException(ErrorCode.EVIDENCE_ALREADY_DELETED);
        }
        return evidence;
    }

    private PartnerReviewer findReviewerByUser(UUID userId) {
        return partnerReviewerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PARTNER_REVIEWER_NOT_FOUND));
    }
}
