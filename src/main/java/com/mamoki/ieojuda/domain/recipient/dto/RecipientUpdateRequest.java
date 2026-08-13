package com.mamoki.ieojuda.domain.recipient.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// "담당자 수정" 화면 - 이름/이메일 수정
public record RecipientUpdateRequest(
        @Schema(description = "담당자 이름", example = "김민수")
        @NotBlank(message = "담당자 이름을 입력해 주세요.") String name,

        @Schema(description = "담당자 이메일", example = "recipient@example.com")
        @NotBlank(message = "담당자 이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.") String email
) {
}
