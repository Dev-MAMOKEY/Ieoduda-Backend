package com.mamoki.ieojuda.domain.confirmer.dto;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;
import java.time.LocalDateTime;

// "지정확인자 수락 이메일" 화면 - 수락/거절 처리 결과
public record ConfirmerDecisionResponse(
        @Schema(description = "확인자 ID") UUID confirmId,
        @Schema(description = "수락 상태", example = "ACCEPTED", allowableValues = {"ACCEPTED", "DECLINED"}) String acceptanceStatus,
        @Schema(description = "수락 시각 (거절 시 null)") LocalDateTime acceptedAt,
        @Schema(description = "문의 사항") String inquiry
) {
    public static ConfirmerDecisionResponse from(Confirmer confirmer) {
        return new ConfirmerDecisionResponse(
                confirmer.getConfirmId(),
                confirmer.getAcceptanceStatus().name(),
                confirmer.getAcceptedAt(),
                confirmer.getInquiry()
        );
    }
}
