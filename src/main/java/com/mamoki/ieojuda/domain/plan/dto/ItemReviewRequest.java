package com.mamoki.ieojuda.domain.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ItemReviewRequest(
        @Schema(description = "검토할 항목 ID", example = "1")
        @NotNull(message = "검토할 항목을 선택해 주세요.") Long itemId,
        @Schema(description = "승인 또는 기각", example = "APPROVE")
        @NotNull(message = "승인 또는 기각 여부를 선택해 주세요.") ItemReviewDecision decision
) {
}
