package com.mamoki.ieojuda.domain.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// issue #52 - 대화 1회 발화 길이 상한(4,000자). OpenAI로 무제한 길이의 입력이 전달되는 것을 막는다
public record LifeAreaMessageRequest(
        @Schema(description = "사용자가 입력한 자연어 발화", example = "아내에게 가족사진 위치를 알려주고 싶어요.")
        @NotBlank(message = "내용을 입력해 주세요.")
        @Size(max = 4000, message = "한 번에 입력할 수 있는 내용은 4,000자 이하여야 합니다.") String content
) {
}
