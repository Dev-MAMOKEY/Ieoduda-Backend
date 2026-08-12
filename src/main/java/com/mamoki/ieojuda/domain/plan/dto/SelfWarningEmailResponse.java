package com.mamoki.ieojuda.domain.plan.dto;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import io.swagger.v3.oas.annotations.media.Schema;

public record SelfWarningEmailResponse(
        @Schema(description = "계획 ID") Long planId,
        @Schema(description = "등록된 본인 경고 이메일") String email,
        @Schema(description = "검증 완료 여부") boolean verified,
        @Schema(description = "검증 메일 발송 성공 여부") boolean emailSent
) {
    public static SelfWarningEmailResponse of(Plan plan, boolean emailSent) {
        return new SelfWarningEmailResponse(
                plan.getPlanId(),
                plan.getSelfWarningEmail(),
                Boolean.TRUE.equals(plan.getSelfWarningEmailVerified()),
                emailSent
        );
    }
}
