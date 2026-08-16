package com.mamoki.ieojuda.domain.partner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record PartnerReviewDecisionRequest(
        @Schema(description = "결정", example = "APPROVE", allowableValues = {"APPROVE", "REJECT", "ADDITIONAL_INFO_REQUESTED"})
        @NotNull(message = "결정을 선택해 주세요.") PartnerReviewDecision decision,
        @Schema(description = "반려 사유 (REJECT일 때 필수)")
        @jakarta.validation.constraints.Size(max = 1000, message = "반려 사유는 1,000자 이하여야 합니다.") String failureReason
) {
    public enum PartnerReviewDecision { APPROVE, REJECT, ADDITIONAL_INFO_REQUESTED }
}
