package com.mamoki.ieojuda.domain.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

// issue #59 - 사건의 증빙 검토를 담당할 외부 파트너사를 운영자가 수동으로 배정
public record AssignPartnerRequest(
        @Schema(description = "배정할 외부 파트너사 ID")
        @NotNull(message = "파트너사를 선택해 주세요.") Long partnerId
) {
}
