package com.mamoki.ieojuda.domain.evidence.dto;

import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// "증빙 삭제 감사" 화면
public record EvidenceDeletionStatusResponse(
        @Schema(description = "증빙 ID") Long evidenceId,
        @Schema(description = "파일명") String fileName,
        @Schema(description = "증빙 종류", example = "DEATH_CERTIFICATE", allowableValues = {"DEATH_CERTIFICATE", "DEATH_REPORT", "POSTMORTEM_REPORT"}) String evidenceType,
        @Schema(description = "검토 완료일 (없으면 null)") LocalDateTime reviewedAt,
        @Schema(description = "삭제 예정일 (없으면 null)") LocalDateTime deleteScheduledAt,
        @Schema(description = "실제 삭제 시각 (없으면 null)") LocalDateTime deletedAt,
        @Schema(description = "무결성 해시(SHA-256)") String integrityHash,
        @Schema(description = "삭제 실패 사유 (없으면 null)") String failureReason,
        @Schema(description = "삭제 예정일을 넘겼는데 아직 삭제 안 됐으면 true (보안 경보 대상)") boolean overdue
) {
    public static EvidenceDeletionStatusResponse from(Evidence evidence) {
        boolean overdue = evidence.getDeletedAt() == null
                && evidence.getDeleteScheduledAt() != null
                && evidence.getDeleteScheduledAt().isBefore(LocalDateTime.now());

        return new EvidenceDeletionStatusResponse(
                evidence.getEvidenceId(),
                evidence.getFileName(),
                evidence.getEvidenceType().name(),
                evidence.getReviewedAt(),
                evidence.getDeleteScheduledAt(),
                evidence.getDeletedAt(),
                evidence.getIntegrityHash(),
                evidence.getFailureReason(),
                overdue
        );
    }
}
