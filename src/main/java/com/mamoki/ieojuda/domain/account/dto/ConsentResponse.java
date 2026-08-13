package com.mamoki.ieojuda.domain.account.dto;

import com.mamoki.ieojuda.domain.account.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ConsentResponse(
        @Schema(description = "필수 동의 완료 여부") boolean agreed,
        @Schema(description = "동의 완료 시각 (아직 미동의면 null)") LocalDateTime agreedAt
) {
    public static ConsentResponse from(User user) {
        return new ConsentResponse(user.getConsentAgreedAt() != null, user.getConsentAgreedAt());
    }
}
