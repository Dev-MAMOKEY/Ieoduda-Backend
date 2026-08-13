package com.mamoki.ieojuda.domain.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// 마이페이지 "변경하기" - 이메일/이름 변경
public record UserUpdateRequest(
        @Schema(description = "새 이메일", example = "namu_k@gmail.com")
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.") String email,

        @Schema(description = "새 이름", example = "홍길동")
        @NotBlank(message = "이름을 입력해 주세요.") String name
) {
}
