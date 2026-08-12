package com.mamoki.ieojuda.domain.confirmer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// "지정 확인자 등록" 화면 - 이름/이메일 입력 폼 한 줄에 대응
public record ConfirmerRegisterRequest(
        @Schema(description = "확인자 이름", example = "김민수")
        @NotBlank(message = "확인자 이름을 입력해 주세요.") String name,

        @Schema(description = "확인자 이메일", example = "confirmer@example.com")
        @NotBlank(message = "확인자 이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.") String email
) {
}
