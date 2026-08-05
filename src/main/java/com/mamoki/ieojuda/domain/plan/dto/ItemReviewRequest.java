package com.mamoki.ieojuda.domain.plan.dto;

import jakarta.validation.constraints.NotNull;

public record ItemReviewRequest(
        @NotNull(message = "검토할 항목을 선택해 주세요.") Long itemId,
        @NotNull(message = "승인 또는 기각 여부를 선택해 주세요.") ItemReviewDecision decision
) {
}
