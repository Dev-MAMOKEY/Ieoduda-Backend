package com.mamoki.ieojuda.domain.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PlanUpdateRequest(
        @Schema(description = "계획 이름", example = "수정된 계획 이름")
        @NotBlank(message = "계획 이름을 입력해 주세요.") String name,
        @Schema(description = "사후 공개 대기 기간(일), 7~30 사이", example = "21")
        @NotNull(message = "대기 기간을 입력해 주세요.") Integer waitingDays
) {
}
