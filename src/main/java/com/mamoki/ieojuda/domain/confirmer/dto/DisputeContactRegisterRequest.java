package com.mamoki.ieojuda.domain.confirmer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// "대기 이의제기 설정" 화면 - 이의 제기 연락처 등록(검증 메일 발송)
public record DisputeContactRegisterRequest(
        @Schema(description = "이의 제기 연락처 이름", example = "이지수")
        @NotBlank(message = "이름을 입력해 주세요.") String name,

        @Schema(description = "이의 제기 연락처 이메일", example = "jisoo@example.com")
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.") String email
) {
}
