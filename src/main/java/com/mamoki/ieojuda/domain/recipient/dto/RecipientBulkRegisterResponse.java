package com.mamoki.ieojuda.domain.recipient.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record RecipientBulkRegisterResponse(
        @Schema(description = "등록 + 발송 결과 목록") List<RecipientRegisterResponse> recipients
) {
}
