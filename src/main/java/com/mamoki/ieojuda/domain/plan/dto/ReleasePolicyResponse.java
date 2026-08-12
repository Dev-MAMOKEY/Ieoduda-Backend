package com.mamoki.ieojuda.domain.plan.dto;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import io.swagger.v3.oas.annotations.media.Schema;

public record ReleasePolicyResponse(
        @Schema(description = "계획 ID") Long planId,
        @Schema(description = "대기 기간(일)") Integer waitingDays
) {
    public static ReleasePolicyResponse from(Plan plan) {
        return new ReleasePolicyResponse(plan.getPlanId(), plan.getWaitingDays());
    }
}
