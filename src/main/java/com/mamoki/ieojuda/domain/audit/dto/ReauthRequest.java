package com.mamoki.ieojuda.domain.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// issue #59 - 고위험 조작(사건 동결 등) 직전 비밀번호 재확인용
public record ReauthRequest(
        @Schema(description = "본인 확인용 현재 비밀번호")
        @NotBlank(message = "비밀번호를 입력해 주세요.") String password
) {
}
