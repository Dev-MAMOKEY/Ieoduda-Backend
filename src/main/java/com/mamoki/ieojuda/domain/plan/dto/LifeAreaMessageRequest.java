package com.mamoki.ieojuda.domain.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LifeAreaMessageRequest(
        @Schema(description = "사용자가 입력한 자연어 발화", example = "아내에게 가족사진 위치를 알려주고 싶어요.")
        @NotBlank(message = "내용을 입력해 주세요.") String content
) {
}
