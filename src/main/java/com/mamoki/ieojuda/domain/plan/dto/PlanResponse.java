package com.mamoki.ieojuda.domain.plan.dto;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record PlanResponse(
        @Schema(description = "계획 ID") Long planId,
        @Schema(description = "계획 이름") String name,
        @Schema(description = "사후 공개 대기 기간(일)") Integer waitingDays,
        @Schema(description = "계획 상태", example = "DRAFT", allowableValues = {"DRAFT", "SEALED", "DEACTIVATED"}) String status,
        @Schema(description = "생성 시각") LocalDateTime createdAt
) {
    public static PlanResponse from(Plan plan) {
        return new PlanResponse(
                plan.getPlanId(),
                plan.getName(),
                plan.getWaitingDays(),
                plan.getStatus().name(),
                plan.getCreatedAt()
        );
    }
}
