package com.mamoki.ieojuda.domain.recipient.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// "역할 담당자 등록" 화면 - [대체 담당자 등록하기] 버튼으로 여는 폼(이름/이메일만 입력)
public record BackupRegisterRequest(
        @Schema(description = "대체 담당자 이름", example = "박지훈")
        @NotBlank(message = "대체 담당자 이름을 입력해 주세요.")
        @jakarta.validation.constraints.Size(max = 100, message = "대체 담당자 이름은 100자 이하여야 합니다.") String name,

        @Schema(description = "대체 담당자 이메일", example = "backup@example.com")
        @NotBlank(message = "대체 담당자 이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @jakarta.validation.constraints.Size(max = 255, message = "대체 담당자 이메일은 255자 이하여야 합니다.") String email
) {
}
