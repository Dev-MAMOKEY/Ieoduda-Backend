package com.mamoki.ieojuda.domain.plan.dto;

import jakarta.validation.constraints.NotBlank;

public record LifeAreaMessageRequest(
        @NotBlank(message = "내용을 입력해 주세요.") String content
) {
}
