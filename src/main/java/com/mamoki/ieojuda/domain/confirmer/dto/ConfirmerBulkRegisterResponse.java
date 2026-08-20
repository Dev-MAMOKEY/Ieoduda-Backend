package com.mamoki.ieojuda.domain.confirmer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ConfirmerBulkRegisterResponse(
        @Schema(description = "등록 + 발송 결과 목록") List<ConfirmerRegisterResponse> confirmers
) {
}
