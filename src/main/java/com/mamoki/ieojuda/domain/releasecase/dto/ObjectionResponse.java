package com.mamoki.ieojuda.domain.releasecase.dto;

import com.mamoki.ieojuda.domain.releasecase.entity.Objection;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;
import java.time.LocalDateTime;

// 이의 제기 접수 결과
public record ObjectionResponse(
        @Schema(description = "이의 제기 ID") UUID objectionId,
        @Schema(description = "접수 상태", example = "RAISED", allowableValues = {"RAISED", "UNDER_REVIEW", "RESOLVED", "DISMISSED"}) String status,
        @Schema(description = "접수 시각") LocalDateTime raisedAt
) {
    public static ObjectionResponse from(Objection objection) {
        return new ObjectionResponse(objection.getObjectionId(), objection.getStatus().name(), objection.getRaisedAt());
    }
}
