package com.mamoki.ieojuda.domain.partner.service;

import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceRepository;
import com.mamoki.ieojuda.domain.partner.dto.PartnerReviewDecisionRequest;
import com.mamoki.ieojuda.domain.partner.dto.PartnerReviewResponse;
import com.mamoki.ieojuda.domain.partner.entity.PartnerReviewer;
import com.mamoki.ieojuda.domain.partner.repository.PartnerReviewerRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.storage.EvidenceStorageClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 명세서 "외부 파트너 증빙 검토" 화면 - 외부 법무·장례 파트너 전용. 역할별 패키지(계획 내용)는 절대 노출하지 않는다.
// reviewId는 evidenceId와 1:1로 취급한다(증빙 1건 = 검토 1건).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnerReviewService {

    private final EvidenceRepository evidenceRepository;
    private final PartnerReviewerRepository partnerReviewerRepository;
    private final EvidenceStorageClient evidenceStorageClient;

    public PartnerReviewResponse getReview(Long reviewId) {
        return PartnerReviewResponse.from(findEvidence(reviewId));
    }

    // 증빙 원본 다운로드 - JSON 응답에 바이너리를 섞지 않기 위해 별도 엔드포인트로 분리
    public byte[] getFile(Long reviewId) {
        return evidenceStorageClient.load(findEvidence(reviewId).getStorageKey());
    }

    @Transactional
    public PartnerReviewResponse decide(Long reviewId, Long userId, PartnerReviewDecisionRequest request) {
        Evidence evidence = findEvidence(reviewId);
        PartnerReviewer reviewer = findReviewerByUser(userId);

        // 이해충돌·권한 만료로 비활성화된 검토자는 결정할 수 없음 (명세서 "다른 검토자에게 재배정")
        if (!Boolean.TRUE.equals(reviewer.getIsActive())) {
            throw new CustomException(ErrorCode.REVIEWER_CONFLICT_OF_INTEREST);
        }

        evidence.assignReviewer(reviewer);

        switch (request.decision()) {
            // 승인되면 계획에 설정된 대기 기간만큼 발송을 미루는 대기 상태로 사건을 전이시킨다
            case APPROVE -> {
                evidence.approve();
                evidence.getReleaseCase().approveEvidenceAndStartWaiting(evidence.getPlan().getWaitingDays());
            }
            case REJECT -> {
                if (request.failureReason() == null || request.failureReason().isBlank()) {
                    throw new CustomException(ErrorCode.INVALID_INPUT);
                }
                evidence.reject(request.failureReason());
                evidence.getReleaseCase().rejectEvidence();
            }
            case ADDITIONAL_INFO_REQUESTED -> evidence.reAdditionalInfo();
        }

        return PartnerReviewResponse.from(evidence);
    }

    private Evidence findEvidence(Long reviewId) {
        return evidenceRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.EVIDENCE_NOT_FOUND));
    }

    private PartnerReviewer findReviewerByUser(Long userId) {
        return partnerReviewerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PARTNER_REVIEWER_NOT_FOUND));
    }
}
