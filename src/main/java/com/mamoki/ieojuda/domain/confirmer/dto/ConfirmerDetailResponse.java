package com.mamoki.ieojuda.domain.confirmer.dto;

import java.util.UUID;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import io.swagger.v3.oas.annotations.media.Schema;

// "역할 점검" 화면 상세 - 이름 클릭 시 해당 확인자 정보
public record ConfirmerDetailResponse(
        @Schema(description = "확인자 ID") UUID confirmId,
        @Schema(description = "확인자 이름") String name,
        @Schema(description = "확인자 이메일") String email,
        @Schema(description = "수락 상태", example = "PENDING", allowableValues = {"PENDING", "ACCEPTED", "DECLINED", "EXPIRED"}) String acceptanceStatus
) {
    public static ConfirmerDetailResponse from(Confirmer confirmer) {
        return new ConfirmerDetailResponse(
                confirmer.getConfirmId(),
                confirmer.getName(),
                confirmer.getEmail(),
                confirmer.getAcceptanceStatus().name()
        );
    }
}
