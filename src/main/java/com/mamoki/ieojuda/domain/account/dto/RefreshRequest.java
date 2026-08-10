package com.mamoki.ieojuda.domain.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @Schema(description = "로그인 시 발급받은 Refresh Token")
        @NotBlank(message = "Refresh Token이 필요합니다.") String refreshToken
) {
}
