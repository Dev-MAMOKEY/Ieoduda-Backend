package com.mamoki.ieojuda.domain.recipient.dto;

import java.util.UUID;

import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import io.swagger.v3.oas.annotations.media.Schema;

// 역할 수락 요청 재전송 결과
public record RecipientAcceptanceEmailResponse(
        @Schema(description = "담당자 ID") UUID recipientId,
        @Schema(description = "담당자 이메일") String email,
        @Schema(description = "수락 상태", example = "PENDING", allowableValues = {"PENDING", "ACCEPTED", "DECLINED", "EXPIRED"}) String acceptanceStatus,
        @Schema(description = "역할 수락 이메일 발송 성공 여부") boolean emailSent,
        @Schema(description = "발송 실패 시 반송 유형 (성공 시 null)", example = "TEMPORARY", allowableValues = {"NONE", "TEMPORARY", "PERMANENT"}) String bounceType
) {
    public static RecipientAcceptanceEmailResponse of(Recipient recipient, boolean emailSent, String bounceType) {
        return new RecipientAcceptanceEmailResponse(
                recipient.getAssigneeId(),
                recipient.getEmail(),
                recipient.getAcceptanceStatus().name(),
                emailSent,
                bounceType
        );
    }
}
