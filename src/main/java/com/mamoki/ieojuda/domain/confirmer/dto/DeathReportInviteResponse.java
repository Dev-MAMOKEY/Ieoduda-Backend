package com.mamoki.ieojuda.domain.confirmer.dto;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// "사망 신고 이메일" 화면 - 신고 링크로 진입했을 때 보여줄 내용
public record DeathReportInviteResponse(
        @Schema(description = "확인자 이름", example = "유지민") String confirmerName,
        @Schema(description = "작성자(계획 소유자) 이름", example = "김나무") String ownerName,
        @Schema(description = "신고 상태", example = "NOT_REPORTED", allowableValues = {"NOT_REPORTED", "REPORTED", "MATCHED", "MISMATCHED"}) String reportStatus,
        @Schema(description = "신고 이메일이 발송된 주소") String email,
        @Schema(description = "신고 링크 만료 시각") LocalDateTime expiresAt,
        @Schema(description = "문의 주소") String contactEmail
) {
    public static DeathReportInviteResponse of(Confirmer confirmer, String ownerName, LocalDateTime expiresAt, String contactEmail) {
        return new DeathReportInviteResponse(
                confirmer.getName(),
                ownerName,
                confirmer.getReportStatus().name(),
                confirmer.getEmail(),
                expiresAt,
                contactEmail
        );
    }
}
