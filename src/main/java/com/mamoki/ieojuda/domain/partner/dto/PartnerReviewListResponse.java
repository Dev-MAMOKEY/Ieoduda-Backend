package com.mamoki.ieojuda.domain.partner.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record PartnerReviewListResponse(
        @Schema(description = "검토 목록") List<PartnerReviewListItemResponse> reviews
) {
}
