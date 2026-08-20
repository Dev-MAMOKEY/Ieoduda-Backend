package com.mamoki.ieojuda.domain.partner.dto;

import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;
import java.time.LocalDateTime;

// "외부 파트너 증빙 검토" 목록 화면 - 역할별 패키지(계획 내용)는 절대 포함하지 않는다.
// 단건 조회(PartnerReviewResponse)와 달리 목록에는 mimeType/integrityHash/reviewedAt/failureReason은 담지 않는다.
public record PartnerReviewListItemResponse(
        @Schema(description = "검토 ID (증빙 ID와 동일)") UUID reviewId,
        @Schema(description = "사망 확인 대상자(계획 작성자) 이름") String targetName,
        @Schema(description = "증빙을 제출한 지정확인자 이름") String confirmerName,
        @Schema(description = "증빙 파일명") String fileName,
        @Schema(description = "증빙 종류", example = "DEATH_CERTIFICATE", allowableValues = {"DEATH_CERTIFICATE", "DEATH_REPORT", "POSTMORTEM_REPORT"}) String evidenceType,
        @Schema(description = "제출 시각") LocalDateTime submittedAt,
        @Schema(description = "검토 상태", example = "PENDING", allowableValues = {"PENDING", "APPROVED", "REJECTED", "ADDITIONAL_INFO_REQUESTED"}) String reviewStatus
) {
    public static PartnerReviewListItemResponse from(Evidence evidence) {
        return new PartnerReviewListItemResponse(
                evidence.getEvidenceId(),
                evidence.getPlan().getUser().getName(),
                evidence.getConfirmer().getName(),
                evidence.getFileName(),
                evidence.getEvidenceType().name(),
                evidence.getSubmittedAt(),
                evidence.getReviewStatus().name()
        );
    }
}
