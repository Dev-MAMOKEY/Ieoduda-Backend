package com.mamoki.ieojuda.domain.postaccess.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// "사후 인계 이메일" 화면 - "인증 코드 입력" 제출
public record OtpVerifyRequest(
        @Schema(description = "화면에 입력한 4자리 인증 코드", example = "1234")
        @NotBlank(message = "인증 코드를 입력해 주세요.") String otpCode
) {
}
