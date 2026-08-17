package com.mamoki.ieojuda.domain.postaccess.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// "사후 인계 이메일" 화면 - OTP 발송 결과
public record OtpSendResponse(
        @Schema(description = "OTP가 발송된 이메일 (마스킹됨)", example = "ji****@naver.com") String maskedEmail,
        @Schema(description = "OTP 만료 시각") LocalDateTime otpExpiresAt,
        @Schema(description = "재발송 가능 시각") LocalDateTime resendAvailableAt
) {
}
