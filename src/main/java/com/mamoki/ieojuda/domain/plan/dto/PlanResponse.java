package com.mamoki.ieojuda.domain.plan.dto;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// 계획(Plan)은 이제 사용자가 만드는 게 아니라 회원가입 시 자동 생성되는, 사후 인계 파이프라인(사망확인/증빙/발송)의 앵커일 뿐이라
// name/waitingDays 같은 사용자 입력값을 갖지 않는다.
public record PlanResponse(
        @Schema(description = "계획 ID") Long planId,
        @Schema(description = "계획 상태", example = "DRAFT", allowableValues = {"DRAFT", "SEALED", "DEACTIVATED"}) String status,
        @Schema(description = "생성 시각") LocalDateTime createdAt
) {
    public static PlanResponse from(Plan plan) {
        return new PlanResponse(plan.getPlanId(), plan.getStatus().name(), plan.getCreatedAt());
    }
}
