package com.mamoki.ieojuda.domain.confirmer.dto;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import io.swagger.v3.oas.annotations.media.Schema;

// 확인자 수정 결과
public record ConfirmerUpdateResponse(
        @Schema(description = "확인자 ID") Long confirmId,
        @Schema(description = "확인자 이름") String name,
        @Schema(description = "확인자 이메일") String email,
        @Schema(description = "수락 상태", example = "PENDING", allowableValues = {"PENDING", "ACCEPTED", "DECLINED", "EXPIRED"}) String acceptanceStatus,
        @Schema(description = "이메일 변경으로 인한 수락 이메일 재발송 성공 여부 (이메일이 바뀌지 않았으면 false)") boolean emailSent,
        @Schema(description = "발송 실패 시 반송 유형 (성공하거나 재발송하지 않은 경우 null)", example = "TEMPORARY", allowableValues = {"NONE", "TEMPORARY", "PERMANENT"}) String bounceType
) {
    public static ConfirmerUpdateResponse of(Confirmer confirmer, boolean emailSent, String bounceType) {
        return new ConfirmerUpdateResponse(
                confirmer.getConfirmId(),
                confirmer.getName(),
                confirmer.getEmail(),
                confirmer.getAcceptanceStatus().name(),
                emailSent,
                bounceType
        );
    }
}
