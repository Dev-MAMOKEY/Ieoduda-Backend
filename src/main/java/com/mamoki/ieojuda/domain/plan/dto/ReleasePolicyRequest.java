package com.mamoki.ieojuda.domain.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

// "대기 이의제기 설정" 화면 - 대기 기간 저장
public record ReleasePolicyRequest(
        @Schema(description = "대기 기간(일)", example = "7", allowableValues = {"7", "14", "21"})
        @NotNull(message = "대기 기간을 선택해 주세요.") Integer waitingDays
) {
}
