package com.mamoki.ieojuda.domain.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PlanCreateRequest(
        // TODO: 로그인/JWT 인증이 붙으면 인증된 사용자로 대체하고 이 필드는 제거한다.
        @NotNull(message = "작성자 정보가 필요합니다.") Long userId,
        @NotBlank(message = "계획 이름을 입력해 주세요.") String name,
        @NotNull(message = "대기 기간을 입력해 주세요.") Integer waitingDays
) {
}
