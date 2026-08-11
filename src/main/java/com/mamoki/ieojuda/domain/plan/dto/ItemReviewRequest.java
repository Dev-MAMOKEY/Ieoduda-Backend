package com.mamoki.ieojuda.domain.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ItemReviewRequest(
        @Schema(description = "승인할 항목 ID", example = "1")
        @NotNull(message = "승인할 항목을 선택해 주세요.") Long itemId
) {
}
