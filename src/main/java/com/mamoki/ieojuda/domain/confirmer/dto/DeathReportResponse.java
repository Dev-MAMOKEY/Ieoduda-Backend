package com.mamoki.ieojuda.domain.confirmer.dto;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;
import java.time.LocalDateTime;

// 사망 신고 접수 결과 - issue #41 재설계: 상대 확인자의 매칭 여부와 무관하게 사건과 증빙 제출 토큰이
// 즉시 함께 발급되므로, 프런트가 이 응답만으로 바로 "공식 증빙 제출" 화면으로 넘어갈 수 있다.
public record DeathReportResponse(
        @Schema(description = "확인자 ID") UUID confirmId,
        @Schema(description = "신고 상태", example = "REPORTED", allowableValues = {"NOT_REPORTED", "REPORTED", "MATCHED", "MISMATCHED"}) String reportStatus,
        @Schema(description = "신고 접수 시각") LocalDateTime reportedAt,
        @Schema(description = "이 신고가 속한 사건 ID") UUID caseId,
        @Schema(description = "증빙 제출용 토큰 - /api/confirmer-acceptances/{token}/death-report(caseId 쿼리 포함)로 바로 제출 가능") String evidenceUploadToken
) {
    public static DeathReportResponse of(Confirmer confirmer, UUID caseId, String evidenceUploadToken) {
        return new DeathReportResponse(
                confirmer.getConfirmId(),
                confirmer.getReportStatus().name(),
                confirmer.getReportedAt(),
                caseId,
                evidenceUploadToken
        );
    }
}
