package com.mamoki.ieojuda.domain.recipient.dto;

import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import io.swagger.v3.oas.annotations.media.Schema;

// 담당자 등록 + 수락 이메일 발송 결과 하나
public record RecipientRegisterResponse(
        @Schema(description = "담당자 ID") Long recipientId,
        @Schema(description = "배정된 항목 ID") Long itemId,
        @Schema(description = "역할 유형", example = "FAMILY_MANAGER", allowableValues = {"FAMILY_MANAGER", "WORK_MANAGER", "RELATIONSHIP_MANAGER"}) String roleType,
        @Schema(description = "공개 범위", example = "FAMILY", allowableValues = {"FAMILY", "WORK", "RELATIONSHIP"}) String disclosureScope,
        @Schema(description = "담당자 이름") String name,
        @Schema(description = "담당자 이메일") String email,
        @Schema(description = "최대 단계 대기 시간(시간 단위)") Integer maxWaitHours,
        @Schema(description = "수락 상태", example = "PENDING", allowableValues = {"PENDING", "ACCEPTED", "DECLINED", "EXPIRED"}) String acceptanceStatus,
        @Schema(description = "역할 수락 이메일 발송 성공 여부") boolean emailSent,
        @Schema(description = "발송 실패 시 반송 유형 (성공 시 null)", example = "TEMPORARY", allowableValues = {"NONE", "TEMPORARY", "PERMANENT"}) String bounceType,
        @Schema(description = "대체 담당자 등록 결과 (미등록 시 null)") BackupRegisterResponse backup
) {
    public static RecipientRegisterResponse of(Recipient recipient, Long itemId, boolean emailSent, String bounceType,
                                                BackupRegisterResponse backup) {
        return new RecipientRegisterResponse(
                recipient.getAssigneeId(),
                itemId,
                recipient.getRoleType().name(),
                recipient.getDisclosureScope() == null ? null : recipient.getDisclosureScope().name(),
                recipient.getName(),
                recipient.getEmail(),
                recipient.getMaxWaitHours(),
                recipient.getAcceptanceStatus().name(),
                emailSent,
                bounceType,
                backup
        );
    }
}
