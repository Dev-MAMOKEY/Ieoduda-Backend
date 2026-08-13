package com.mamoki.ieojuda.domain.partner.dto;

import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// "외부 파트너 증빙 검토" 화면 - 역할별 패키지(계획 내용)는 절대 포함하지 않는다
// TODO: 사망 확인 대상자(계획 작성자 본인)의 이름·생년월일을 저장할 곳이 아직 없어서, 지금은 신고한 확인자 정보로 대체함
public record PartnerReviewResponse(
        @Schema(description = "검토 ID (증빙 ID와 동일)") Long reviewId,
        @Schema(description = "신고한 확인자 이름") String reporterName,
        @Schema(description = "신고한 확인자와의 관계") String reporterRelationship,
        @Schema(description = "증빙 파일명") String fileName,
        @Schema(description = "증빙 MIME 타입") String mimeType,
        @Schema(description = "제출 시각") LocalDateTime submittedAt,
        @Schema(description = "무결성 해시(SHA-256)") String integrityHash,
        @Schema(description = "검토 상태", example = "PENDING", allowableValues = {"PENDING", "APPROVED", "REJECTED", "ADDITIONAL_INFO_REQUESTED"}) String reviewStatus,
        @Schema(description = "검토 완료 시각 (없으면 null)") LocalDateTime reviewedAt,
        @Schema(description = "반려 사유 (없으면 null)") String failureReason
) {
    public static PartnerReviewResponse from(Evidence evidence) {
        return new PartnerReviewResponse(
                evidence.getEvidenceId(),
                evidence.getConfirmer().getName(),
                evidence.getConfirmer().getRelationship() == null ? null : evidence.getConfirmer().getRelationship().name(),
                evidence.getFileName(),
                evidence.getMimeType(),
                evidence.getSubmittedAt(),
                evidence.getIntegrityHash(),
                evidence.getReviewStatus().name(),
                evidence.getReviewedAt(),
                evidence.getFailureReason()
        );
    }
}
