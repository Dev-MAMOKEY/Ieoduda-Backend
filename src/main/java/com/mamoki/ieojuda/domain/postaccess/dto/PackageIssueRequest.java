package com.mamoki.ieojuda.domain.postaccess.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PackageIssueRequest(
        @Schema(description = "문제가 발생한 행동 ID", example = "12")
        @NotNull(message = "행동 ID를 입력해 주세요.") Long actionId,

        @Schema(description = "문제 사유", example = "해당 계정에 접근할 수 없습니다.")
        @NotBlank(message = "문제 사유를 입력해 주세요.")
        @jakarta.validation.constraints.Size(max = 1000, message = "문제 사유는 1000자 이하여야 합니다.") String reason
) {
}
