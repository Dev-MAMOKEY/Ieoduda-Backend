package com.mamoki.ieojuda.domain.partner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// "외부 파트너 증빙 검토" 화면 - 승인/반려 요청/추가 자료 요청
// 검토자는 요청 바디가 아니라 로그인한 계정(JWT principal)에서 식별한다.
// issue #59 - 증빙 판정은 고위험 조작이라 비밀번호 재확인(password)을 함께 받는다.
public record PartnerReviewDecisionRequest(
        @Schema(description = "결정", example = "APPROVE", allowableValues = {"APPROVE", "REJECT", "ADDITIONAL_INFO_REQUESTED"})
        @NotNull(message = "결정을 선택해 주세요.") PartnerReviewDecision decision,
        @Schema(description = "반려 사유 (REJECT일 때 필수)")
        @jakarta.validation.constraints.Size(max = 1000, message = "반려 사유는 1,000자 이하여야 합니다.") String failureReason,
        @Schema(description = "본인 확인용 현재 비밀번호")
        @NotBlank(message = "비밀번호를 입력해 주세요.") String password
) {
    public enum PartnerReviewDecision {
        APPROVE, REJECT, ADDITIONAL_INFO_REQUESTED
    }
}
