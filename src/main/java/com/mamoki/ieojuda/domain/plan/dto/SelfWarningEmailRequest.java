package com.mamoki.ieojuda.domain.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// "대기 이의제기 설정" 화면 - 본인 경고 이메일 등록(검증 메일 발송)
public record SelfWarningEmailRequest(
        @Schema(description = "실행 신고가 오면 가장 먼저 경고 메일을 받을 본인 주소", example = "namu_k@gmail.com")
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @jakarta.validation.constraints.Size(max = 255, message = "이메일은 255자 이하여야 합니다.") String email
) {
}
