package com.mamoki.ieojuda.domain.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// "대기 이의제기 설정" 화면 - 대기 기간 저장
public record ReleasePolicyRequest(
        @Schema(description = "대기 기간(일)", example = "7", minimum = "7", maximum = "30")
        @NotNull(message = "대기 기간을 선택해 주세요.")
        @Min(value = 7, message = "대기 기간은 7일 이상이어야 합니다.")
        @Max(value = 30, message = "대기 기간은 30일 이하여야 합니다.")
        Integer waitingDays
) {
}
