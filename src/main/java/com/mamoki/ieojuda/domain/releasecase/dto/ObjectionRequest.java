package com.mamoki.ieojuda.domain.releasecase.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// "무언가 잘못됐다면?" 카드 - 이의 제기 사유 입력
public record ObjectionRequest(
        @Schema(description = "이의 제기 사유", example = "본인이 살아있는데 사망으로 잘못 확인되었습니다.")
        @NotBlank(message = "이의 제기 사유를 입력해 주세요.") String reason
) {
}
