package com.mamoki.ieojuda.domain.postaccess.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerifyRequest(
        @Schema(description = "4자리 OTP 코드", example = "1234")
        @NotBlank(message = "OTP 코드를 입력해 주세요.")
        @Pattern(regexp = "\\d{4}", message = "OTP 코드는 4자리 숫자여야 합니다.") String otpCode
) {
}
