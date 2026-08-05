package com.mamoki.ieojuda.domain.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PlanCreateRequest(
        // TODO: 로그인/JWT 인증이 붙으면 인증된 사용자로 대체하고 이 필드는 제거한다.
        @Schema(description = "작성자 user_id (임시 - 인증 붙으면 제거 예정)", example = "1")
        @NotNull(message = "작성자 정보가 필요합니다.") Long userId,
        @Schema(description = "계획 이름", example = "내 유고 계획")
        @NotBlank(message = "계획 이름을 입력해 주세요.") String name,
        @Schema(description = "사후 공개 대기 기간(일), 7~30 사이", example = "14")
        @NotNull(message = "대기 기간을 입력해 주세요.") Integer waitingDays
) {
}
