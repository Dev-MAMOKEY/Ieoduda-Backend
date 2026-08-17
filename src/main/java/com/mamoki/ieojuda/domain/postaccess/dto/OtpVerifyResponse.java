package com.mamoki.ieojuda.domain.postaccess.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// "사후 인계 이메일" 화면 - OTP 인증 성공 시 발급되는 열람 세션. 이 값으로 역할별 사후 패키지를 조회한다.
public record OtpVerifyResponse(
        @Schema(description = "열람 세션 식별자") String accessSessionId,
        @Schema(description = "열람 세션 만료 시각") LocalDateTime sessionExpiresAt
) {
}
