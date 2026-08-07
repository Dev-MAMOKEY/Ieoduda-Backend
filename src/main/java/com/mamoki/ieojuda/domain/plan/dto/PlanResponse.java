package com.mamoki.ieojuda.domain.plan.dto;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public record PlanResponse(
        @Schema(description = "계획 ID") Long planId,
        @Schema(description = "계획 이름") String name,
        @Schema(description = "사후 공개 대기 기간(일)") Integer waitingDays,
        @Schema(description = "계획 상태", example = "DRAFT", allowableValues = {"DRAFT", "SEALED", "DEACTIVATED"}) String status,
        @Schema(description = "생성 시각") LocalDateTime createdAt,
        @Schema(description = "자동 생성된 삶의 구역 3개 (가족/관계정리/업무연속성). 계획 생성 시에만 채워서 반환하며, 그 외 조회에서는 빈 목록일 수 있음")
        List<LifeAreaResponse> lifeAreas
) {
    public static PlanResponse from(Plan plan) {
        return from(plan, List.of());
    }

    public static PlanResponse from(Plan plan, List<LifeAreaResponse> lifeAreas) {
        return new PlanResponse(
                plan.getPlanId(),
                plan.getName(),
                plan.getWaitingDays(),
                plan.getStatus().name(),
                plan.getCreatedAt(),
                lifeAreas
        );
    }
}
