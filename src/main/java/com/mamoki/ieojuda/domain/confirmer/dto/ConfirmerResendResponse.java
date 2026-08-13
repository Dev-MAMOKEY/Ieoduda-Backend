package com.mamoki.ieojuda.domain.confirmer.dto;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import io.swagger.v3.oas.annotations.media.Schema;

// 지정 확인자 수락 요청 재전송 결과
public record ConfirmerResendResponse(
        @Schema(description = "확인자 ID") Long confirmId,
        @Schema(description = "확인자 이메일") String email,
        @Schema(description = "수락 상태", example = "PENDING", allowableValues = {"PENDING", "ACCEPTED", "DECLINED", "EXPIRED"}) String acceptanceStatus,
        @Schema(description = "수락 이메일 발송 성공 여부") boolean emailSent,
        @Schema(description = "발송 실패 시 반송 유형 (성공 시 null)", example = "TEMPORARY", allowableValues = {"NONE", "TEMPORARY", "PERMANENT"}) String bounceType
) {
    public static ConfirmerResendResponse of(Confirmer confirmer, boolean emailSent, String bounceType) {
        return new ConfirmerResendResponse(
                confirmer.getConfirmId(),
                confirmer.getEmail(),
                confirmer.getAcceptanceStatus().name(),
                emailSent,
                bounceType
        );
    }
}
