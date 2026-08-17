package com.mamoki.ieojuda.domain.partner.service;

import java.time.LocalDateTime;
import java.util.UUID;

import com.mamoki.ieojuda.domain.account.entity.AdminPermission;
import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.audit.entity.AdminActionType;
import com.mamoki.ieojuda.domain.audit.service.AdminActionAuditService;
import com.mamoki.ieojuda.domain.confirmer.entity.ReportStatus;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import com.mamoki.ieojuda.domain.evidence.entity.EvidenceDownloadToken;
import com.mamoki.ieojuda.domain.evidence.entity.EvidenceReviewStatus;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceDownloadTokenRepository;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceRepository;
import com.mamoki.ieojuda.domain.partner.dto.EvidenceDownloadLinkResponse;
import com.mamoki.ieojuda.domain.partner.dto.PartnerReviewDecisionRequest;
import com.mamoki.ieojuda.domain.partner.dto.PartnerReviewListItemResponse;
import com.mamoki.ieojuda.domain.partner.dto.PartnerReviewListResponse;
import com.mamoki.ieojuda.domain.partner.dto.PartnerReviewResponse;
import com.mamoki.ieojuda.domain.partner.entity.PartnerReviewer;
import com.mamoki.ieojuda.domain.partner.repository.PartnerReviewerRepository;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.service.ReleaseCaseWarningService;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.idempotency.service.IdempotencyGuard;
import com.mamoki.ieojuda.global.security.PermissionGuard;
import com.mamoki.ieojuda.global.security.ReauthGuard;
import com.mamoki.ieojuda.global.storage.EvidenceStorageClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 명세서 "외부 파트너 증빙 검토" 화면 - 외부 법무·장례 파트너 전용. 역할별 패키지(계획 내용)는 절대 노출하지 않는다.
// reviewId는 evidenceId와 1:1로 취급한다(증빙 1건 = 검토 1건).
// issue #59 - EVIDENCE_REVIEW 세부 권한 + 판정은 재인증 필요. 배정/소속 개념은 폐지되어 EVIDENCE_REVIEW
// 권한만 있으면 모든 증빙을 조회·다운로드·판정할 수 있다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnerReviewService {

    // 다운로드 링크는 발급 즉시 쓰는 것을 전제로 하므로 짧게 둔다
    private static final long DOWNLOAD_TOKEN_TTL_MINUTES = 5;

    private final EvidenceRepository evidenceRepository;
    private final ConfirmerRepository confirmerRepository;
    private final PartnerReviewerRepository partnerReviewerRepository;
    private final EvidenceDownloadTokenRepository evidenceDownloadTokenRepository;
    private final EvidenceStorageClient evidenceStorageClient;
    private final PermissionGuard permissionGuard;
    private final ReauthGuard reauthGuard;
    private final AdminActionAuditService adminActionAuditService;
    private final IdempotencyGuard idempotencyGuard;
    private final SecurityTokenService securityTokenService;
    private final ReleaseCaseWarningService releaseCaseWarningService;

    public PartnerReviewResponse getReview(UUID userId, UUID reviewId) {
        permissionGuard.require(userId, AdminPermission.EVIDENCE_REVIEW);
        Evidence evidence = findEvidence(reviewId);
        findActiveReviewerByUser(userId);
        return PartnerReviewResponse.from(evidence);
    }

    // issue #87 - EVIDENCE_REVIEW 권한만 있으면 전체를 조회한다.
    public PartnerReviewListResponse getReviews(UUID userId, EvidenceReviewStatus status) {
        permissionGuard.require(userId, AdminPermission.EVIDENCE_REVIEW);
        List<PartnerReviewListItemResponse> reviews = evidenceRepository.findAllByReviewStatus(status)
                .stream()
                .map(PartnerReviewListItemResponse::from)
                .toList();
        return new PartnerReviewListResponse(reviews);
    }

    // issue #43 - 원본을 직접 스트리밍하지 않고, 먼저 짧은 수명의 1회성 다운로드 토큰을 발급한다.
    // 실제 원본은 이 토큰을 소비하는 downloadFile()에서만 내려간다.
    @Transactional
    public EvidenceDownloadLinkResponse requestDownloadLink(UUID userId, UUID reviewId) {
        permissionGuard.require(userId, AdminPermission.EVIDENCE_REVIEW);
        Evidence evidence = findEvidence(reviewId);
        PartnerReviewer reviewer = findActiveReviewerByUser(userId);

        String plainToken = TokenProvider.generatePlainToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(DOWNLOAD_TOKEN_TTL_MINUTES);
        evidenceDownloadTokenRepository.save(EvidenceDownloadToken.builder()
                .tokenHash(TokenProvider.hashToken(plainToken))
                .evidence(evidence)
                .reviewer(reviewer)
                .expiresAt(expiresAt)
                .build());

        return new EvidenceDownloadLinkResponse(plainToken, expiresAt);
    }

    // 증빙 원본 다운로드 - 1회성 토큰을 소비해야 하고, 호출자 기준으로 감사된다
    @Transactional
    public byte[] downloadFile(UUID userId, UUID reviewId, String plainToken) {
        User actor = permissionGuard.require(userId, AdminPermission.EVIDENCE_REVIEW);
        Evidence evidence = findEvidence(reviewId);
        findActiveReviewerByUser(userId);

        EvidenceDownloadToken token = evidenceDownloadTokenRepository.findByTokenHash(TokenProvider.hashToken(plainToken))
                .orElseThrow(() -> new CustomException(ErrorCode.TOKEN_INVALID));
        if (!token.getEvidence().getEvidenceId().equals(evidence.getEvidenceId())) {
            throw new CustomException(ErrorCode.TOKEN_INVALID);
        }
        if (token.isExpired()) {
            throw new CustomException(ErrorCode.ACCESS_LINK_EXPIRED);
        }
        int updated = evidenceDownloadTokenRepository.markUsedIfUnused(token.getTokenId(), LocalDateTime.now());
        if (updated == 0) {
            throw new CustomException(ErrorCode.ACCESS_LINK_ALREADY_USED);
        }

        byte[] file = evidenceStorageClient.load(evidence.getStorageKey());
        adminActionAuditService.record(actor, AdminActionType.EVIDENCE_DOWNLOAD, reviewId, true, null);
        return file;
    }

    @Transactional
    public PartnerReviewResponse decide(UUID reviewId, UUID userId, PartnerReviewDecisionRequest request, String idempotencyKey) {
        User actor = permissionGuard.require(userId, AdminPermission.EVIDENCE_REVIEW);
        Evidence evidence = findEvidence(reviewId);
        PartnerReviewer reviewer = findActiveReviewerByUser(userId);

        // issue #43 - 판정을 1회 상태 전이로 제한한다. APPROVED/REJECTED는 종결 상태라 다시 판정할 수 없다
        // (ADDITIONAL_INFO_REQUESTED는 종결이 아니라 추가 자료를 받은 뒤 다시 판정할 수 있어야 한다).
        if (evidence.getReviewStatus() == EvidenceReviewStatus.APPROVED
                || evidence.getReviewStatus() == EvidenceReviewStatus.REJECTED) {
            throw new CustomException(ErrorCode.EVIDENCE_ALREADY_DECIDED);
        }

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

        // 배정/소속 개념이 없으므로, 실제로 판정을 내린 사람이 누구인지는 판정 시점에만 기록한다
        // (표시·감사용 - 접근 제어에는 쓰지 않는다).
        evidence.assignReviewer(reviewer);

        switch (request.decision()) {
            // issue #45 - "여러 증빙의 승인 정책" - 사건에 매칭된 확인자 각각이 낸 증빙이 전부 승인돼야
            // 대기(WAITING)로 넘어간다. 아직 다 안 됐으면 EVIDENCE_APPROVED(부분 승인) 상태로만 남겨둔다.
            case APPROVE -> {
                ReleaseCase releaseCase = evidence.getReleaseCase();
                long alreadyApprovedCount = evidenceRepository.countByReleaseCase_CaseIdAndReviewStatus(
                        releaseCase.getCaseId(), EvidenceReviewStatus.APPROVED);
                evidence.approve();
                long requiredCount = confirmerRepository.findByPlan_PlanIdAndReportStatus(
                        releaseCase.getPlan().getPlanId(), ReportStatus.MATCHED).size();

                if (alreadyApprovedCount + 1 >= requiredCount) {
                    // 이의 제기 연락처 전원에게 경고를 보내 발송이 확인된 뒤에만 WAITING으로 전이한다
                    // (UPLOAD_EVIDENCE 토큰 폐기도 이 메서드 안에서 함께 처리됨) - 발송 실패 시 전이하지 않고 동결한다.
                    releaseCaseWarningService.sendDisputeWarningsAndStartWaiting(releaseCase, evidence.getPlan().getWaitingDays());
                } else {
                    releaseCase.markEvidencePartiallyApproved();
                }
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

    // getReview/downloadFile/decide 세 경로가 모두 이 메서드를 거치므로, 원본이 이미 삭제된 증빙을
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

    // 이해충돌·권한 만료로 비활성화된 검토자는 조회·다운로드·판정 어느 것도 할 수 없음 (명세서 "다른 검토자에게 재배정")
    private PartnerReviewer findActiveReviewerByUser(UUID userId) {
        PartnerReviewer reviewer = partnerReviewerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PARTNER_REVIEWER_NOT_FOUND));
        if (!Boolean.TRUE.equals(reviewer.getIsActive())) {
            throw new CustomException(ErrorCode.REVIEWER_CONFLICT_OF_INTEREST);
        }
        return reviewer;
    }
}
