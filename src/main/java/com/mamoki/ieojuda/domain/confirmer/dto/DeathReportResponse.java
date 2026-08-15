package com.mamoki.ieojuda.domain.confirmer.dto;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// 사망 신고 접수 결과
public record DeathReportResponse(
        @Schema(description = "확인자 ID") Long confirmId,
        @Schema(description = "신고 상태", example = "REPORTED", allowableValues = {"NOT_REPORTED", "REPORTED", "MATCHED", "MISMATCHED"}) String reportStatus,
        @Schema(description = "신고 접수 시각") LocalDateTime reportedAt
) {
    public static DeathReportResponse from(Confirmer confirmer) {
        return new DeathReportResponse(
                confirmer.getConfirmId(),
                confirmer.getReportStatus().name(),
                confirmer.getReportedAt()
        );
    }
}
