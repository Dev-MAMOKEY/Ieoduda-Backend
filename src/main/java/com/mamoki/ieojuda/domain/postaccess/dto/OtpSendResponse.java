package com.mamoki.ieojuda.domain.postaccess.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// "사후 인계 이메일" 화면 - "인증번호 발송하기" / "코드 재발송" 결과
public record OtpSendResponse(
        @Schema(description = "인증 코드를 발송한 주소") String email,
        @Schema(description = "인증 코드 만료 시각") LocalDateTime otpExpiresAt
) {
    public static OtpSendResponse of(String email, LocalDateTime otpExpiresAt) {
        return new OtpSendResponse(email, otpExpiresAt);
    }
}
