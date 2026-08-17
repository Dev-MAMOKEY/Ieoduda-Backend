package com.mamoki.ieojuda.domain.releasecase.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// 이메일 속 취소 링크 클릭 - 작성자 본인 확인용 토큰
public record CaseCancellationRequest(
        @Schema(description = "취소 링크 토큰")
        @NotBlank(message = "토큰을 입력해 주세요.") String token
) {
}
