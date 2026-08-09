package com.mamoki.ieojuda.domain.account.dto;

import com.mamoki.ieojuda.domain.account.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

public record SignupResponse(
        @Schema(description = "사용자 ID") Long userId,
        @Schema(description = "이메일") String email,
        @Schema(description = "이름") String name
) {
    public static SignupResponse from(User user) {
        return new SignupResponse(user.getUserId(), user.getEmail(), user.getName());
    }
}
