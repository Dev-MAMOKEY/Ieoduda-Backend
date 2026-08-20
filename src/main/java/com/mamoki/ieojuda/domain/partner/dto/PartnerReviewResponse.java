package com.mamoki.ieojuda.domain.partner.dto;

import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;
import java.time.LocalDateTime;

// "외부 파트너 증빙 검토" 화면 - 역할별 패키지(계획 내용)는 절대 포함하지 않는다.
// issue #62 - 대상자 이름은 신고한 확인자가 아니라 사망 확인 대상자(계획 작성자) 본인의 것이어야 하므로
// Plan.user에서 가져온다. "신고자와의 관계"는 대상자 본인에게는 의미가 없는 필드라 제거했다.
public record PartnerReviewResponse(
        @Schema(description = "검토 ID (증빙 ID와 동일)") UUID reviewId,
        @Schema(description = "사망 확인 대상자(계획 작성자) 이름") String targetName,
        @Schema(description = "증빙을 제출한 지정확인자 이름") String confirmerName,
        @Schema(description = "증빙 파일명") String fileName,
        @Schema(description = "증빙 MIME 타입") String mimeType,
        @Schema(description = "증빙 종류", example = "DEATH_CERTIFICATE", allowableValues = {"DEATH_CERTIFICATE", "DEATH_REPORT", "POSTMORTEM_REPORT"}) String evidenceType,
        @Schema(description = "제출 시각") LocalDateTime submittedAt,
        @Schema(description = "무결성 해시(SHA-256)") String integrityHash,
        @Schema(description = "검토 상태", example = "PENDING", allowableValues = {"PENDING", "APPROVED", "REJECTED", "ADDITIONAL_INFO_REQUESTED"}) String reviewStatus,
        @Schema(description = "검토 완료 시각 (없으면 null)") LocalDateTime reviewedAt,
        @Schema(description = "반려 사유 (없으면 null)") String failureReason
) {
    public static PartnerReviewResponse from(Evidence evidence) {
        return new PartnerReviewResponse(
                evidence.getEvidenceId(),
                evidence.getPlan().getUser().getName(),
                evidence.getConfirmer().getName(),
                evidence.getFileName(),
                evidence.getMimeType(),
                evidence.getEvidenceType().name(),
                evidence.getSubmittedAt(),
                evidence.getIntegrityHash(),
                evidence.getReviewStatus().name(),
                evidence.getReviewedAt(),
                evidence.getFailureReason()
        );
    }
}
