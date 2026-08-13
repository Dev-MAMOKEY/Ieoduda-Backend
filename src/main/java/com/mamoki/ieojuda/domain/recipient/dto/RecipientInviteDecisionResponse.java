package com.mamoki.ieojuda.domain.recipient.dto;

import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// "역할 수락 이메일" 화면 - 수락/거절 처리 결과
public record RecipientInviteDecisionResponse(
        @Schema(description = "담당자 ID") Long assigneeId,
        @Schema(description = "수락 상태", example = "ACCEPTED", allowableValues = {"ACCEPTED", "DECLINED"}) String acceptanceStatus,
        @Schema(description = "수락 시각 (거절 시 null)") LocalDateTime acceptedAt,
        @Schema(description = "문의 사항") String inquiry
) {
    public static RecipientInviteDecisionResponse from(Recipient recipient) {
        return new RecipientInviteDecisionResponse(
                recipient.getAssigneeId(),
                recipient.getAcceptanceStatus().name(),
                recipient.getAcceptedAt(),
                recipient.getInquiry()
        );
    }
}
