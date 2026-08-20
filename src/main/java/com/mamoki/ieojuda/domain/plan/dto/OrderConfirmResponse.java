package com.mamoki.ieojuda.domain.plan.dto;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// "순서 확정하기" 결과
public record OrderConfirmResponse(
        @Schema(description = "순서 확정 시각") LocalDateTime confirmedAt
) {
    public static OrderConfirmResponse from(Plan plan) {
        return new OrderConfirmResponse(plan.getOrderConfirmedAt());
    }
}
