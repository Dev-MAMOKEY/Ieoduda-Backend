package com.mamoki.ieojuda.domain.recipient.dto;

import java.util.UUID;

import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import io.swagger.v3.oas.annotations.media.Schema;

// 대체 담당자 등록 + 수락 이메일 발송 결과
public record BackupRegisterResponse(
        @Schema(description = "대체 담당자 ID") UUID recipientId,
        @Schema(description = "대체 담당자 이름") String name,
        @Schema(description = "대체 담당자 이메일") String email,
        @Schema(description = "수락 상태", example = "PENDING", allowableValues = {"PENDING", "ACCEPTED", "DECLINED", "EXPIRED"}) String acceptanceStatus,
        @Schema(description = "역할 수락 이메일 발송 성공 여부") boolean emailSent,
        @Schema(description = "발송 실패 시 반송 유형 (성공 시 null)", example = "TEMPORARY", allowableValues = {"NONE", "TEMPORARY", "PERMANENT"}) String bounceType
) {
    public static BackupRegisterResponse of(Recipient backup, boolean emailSent, String bounceType) {
        return new BackupRegisterResponse(
                backup.getAssigneeId(),
                backup.getName(),
                backup.getEmail(),
                backup.getAcceptanceStatus().name(),
                emailSent,
                bounceType
        );
    }
}
