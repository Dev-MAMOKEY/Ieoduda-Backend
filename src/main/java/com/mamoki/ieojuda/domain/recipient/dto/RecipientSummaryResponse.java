package com.mamoki.ieojuda.domain.recipient.dto;

import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import io.swagger.v3.oas.annotations.media.Schema;

// "역할 점검" 화면 상단 이름 목록 - row 단위 그대로 노출 (같은 사람이 여러 항목을 맡으면 이름이 중복될 수 있음)
public record RecipientSummaryResponse(
        @Schema(description = "담당자 ID") Long assigneeId,
        @Schema(description = "담당자 이름") String name,
        @Schema(description = "역할 유형", example = "FAMILY_MANAGER", allowableValues = {"FAMILY_MANAGER", "WORK_MANAGER", "RELATIONSHIP_MANAGER"}) String roleType,
        @Schema(description = "수락 상태", example = "PENDING", allowableValues = {"PENDING", "ACCEPTED", "DECLINED", "EXPIRED"}) String acceptanceStatus
) {
    public static RecipientSummaryResponse from(Recipient recipient) {
        return new RecipientSummaryResponse(
                recipient.getAssigneeId(),
                recipient.getName(),
                recipient.getRoleType().name(),
                recipient.getAcceptanceStatus().name()
        );
    }
}
