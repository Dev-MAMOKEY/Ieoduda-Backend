package com.mamoki.ieojuda.domain.plan.dto;

import com.mamoki.ieojuda.domain.plan.entity.Plan;

import java.time.LocalDateTime;

public record PlanResponse(
        Long planId,
        String name,
        Integer waitingDays,
        String status,
        LocalDateTime createdAt
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
