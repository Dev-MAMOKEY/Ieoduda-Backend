package com.mamoki.ieojuda.domain.evidence.dto;

import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;
import java.time.LocalDateTime;

// "공식 증빙 자료 제출" 화면 - 제출 결과
public record EvidenceSubmitResponse(
        @Schema(description = "증빙 ID") UUID evidenceId,
        @Schema(description = "파일 이름") String fileName,
        @Schema(description = "검토 상태", example = "PENDING", allowableValues = {"PENDING", "APPROVED", "REJECTED", "ADDITIONAL_INFO_REQUESTED"}) String reviewStatus,
        @Schema(description = "제출 시각") LocalDateTime submittedAt
) {
    public static EvidenceSubmitResponse from(Evidence evidence) {
        return new EvidenceSubmitResponse(
                evidence.getEvidenceId(),
                evidence.getFileName(),
                evidence.getReviewStatus().name(),
                evidence.getSubmittedAt()
        );
    }
}
