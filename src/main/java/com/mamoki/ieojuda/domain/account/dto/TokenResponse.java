package com.mamoki.ieojuda.domain.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponse(
        @Schema(description = "Access Token (1시간 유효)") String accessToken,
        @Schema(description = "Refresh Token (14일 유효)") String refreshToken
) {
}
