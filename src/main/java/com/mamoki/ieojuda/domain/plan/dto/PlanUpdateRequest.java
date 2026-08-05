package com.mamoki.ieojuda.domain.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PlanUpdateRequest(
        @NotBlank(message = "계획 이름을 입력해 주세요.") String name,
        @NotNull(message = "대기 기간을 입력해 주세요.") Integer waitingDays
) {
}
