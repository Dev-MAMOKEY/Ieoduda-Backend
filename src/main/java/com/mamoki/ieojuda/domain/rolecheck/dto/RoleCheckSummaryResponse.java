package com.mamoki.ieojuda.domain.rolecheck.dto;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import io.swagger.v3.oas.annotations.media.Schema;

// "역할 점검" 화면 상단 이름 목록 - 역할 담당자와 지정 확인자를 함께 노출
public record RoleCheckSummaryResponse(
        @Schema(description = "구분", example = "RECIPIENT", allowableValues = {"RECIPIENT", "CONFIRMER"}) String type,
        @Schema(description = "담당자 ID 또는 확인자 ID") Long id,
        @Schema(description = "이름") String name,
        @Schema(description = "수락 상태", example = "PENDING", allowableValues = {"PENDING", "ACCEPTED", "DECLINED", "EXPIRED"}) String acceptanceStatus
) {
    public static RoleCheckSummaryResponse from(Recipient recipient) {
        return new RoleCheckSummaryResponse(
                RoleCheckType.RECIPIENT.name(),
                recipient.getAssigneeId(),
                recipient.getName(),
                recipient.getAcceptanceStatus().name()
        );
    }

    public static RoleCheckSummaryResponse from(Confirmer confirmer) {
        return new RoleCheckSummaryResponse(
                RoleCheckType.CONFIRMER.name(),
                confirmer.getConfirmId(),
                confirmer.getName(),
                confirmer.getAcceptanceStatus().name()
        );
    }
}
