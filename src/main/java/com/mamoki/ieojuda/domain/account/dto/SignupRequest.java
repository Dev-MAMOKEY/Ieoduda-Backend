package com.mamoki.ieojuda.domain.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SignupRequest(
        @Schema(description = "이메일", example = "user@example.com")
        @NotBlank(message = "이메일을 입력해 주세요.") @Email(message = "이메일 형식이 올바르지 않습니다.") String email,

        @Schema(description = "비밀번호", example = "password1234")
        @NotBlank(message = "비밀번호를 입력해 주세요.") String password,

        @Schema(description = "비밀번호 확인", example = "password1234")
        @NotBlank(message = "비밀번호 확인을 입력해 주세요.") String passwordConfirm,

        @Schema(description = "이름", example = "홍길동")
        @NotBlank(message = "이름을 입력해 주세요.") String name
) {
}
