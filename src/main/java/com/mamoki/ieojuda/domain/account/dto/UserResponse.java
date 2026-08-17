package com.mamoki.ieojuda.domain.account.dto;

import java.util.UUID;

import com.mamoki.ieojuda.domain.account.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserResponse(
        @Schema(description = "사용자 ID") UUID userId,
        @Schema(description = "이메일") String email,
        @Schema(description = "이름") String name
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getUserId(), user.getEmail(), user.getName());
    }
}
