package com.mamoki.ieojuda.domain.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(description = "이메일", example = "user@example.com")
        @NotBlank(message = "이메일을 입력해 주세요.")
        @jakarta.validation.constraints.Size(max = 255, message = "이메일은 255자 이하여야 합니다.") String email,

        @Schema(description = "비밀번호", example = "password1234")
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @jakarta.validation.constraints.Size(max = 128, message = "비밀번호는 128자 이하여야 합니다.") String password
) {
}
