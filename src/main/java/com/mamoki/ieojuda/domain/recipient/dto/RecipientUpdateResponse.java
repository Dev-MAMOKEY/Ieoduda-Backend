package com.mamoki.ieojuda.domain.recipient.dto;

import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import io.swagger.v3.oas.annotations.media.Schema;

// 담당자 수정 결과
public record RecipientUpdateResponse(
        @Schema(description = "담당자 ID") Long assigneeId,
        @Schema(description = "담당자 이름") String name,
        @Schema(description = "담당자 이메일") String email,
        @Schema(description = "역할 유형", example = "FAMILY_MANAGER", allowableValues = {"FAMILY_MANAGER", "WORK_MANAGER", "RELATIONSHIP_MANAGER"}) String roleType,
        @Schema(description = "수락 상태", example = "PENDING", allowableValues = {"PENDING", "ACCEPTED", "DECLINED", "EXPIRED"}) String acceptanceStatus,
        @Schema(description = "이메일 변경으로 인한 수락 이메일 재발송 성공 여부 (이메일이 바뀌지 않았으면 false)") boolean emailSent,
        @Schema(description = "발송 실패 시 반송 유형 (성공하거나 재발송하지 않은 경우 null)", example = "TEMPORARY", allowableValues = {"NONE", "TEMPORARY", "PERMANENT"}) String bounceType
) {
    public static RecipientUpdateResponse of(Recipient recipient, boolean emailSent, String bounceType) {
        return new RecipientUpdateResponse(
                recipient.getAssigneeId(),
                recipient.getName(),
                recipient.getEmail(),
                recipient.getRoleType().name(),
                recipient.getAcceptanceStatus().name(),
                emailSent,
                bounceType
        );
    }
}
