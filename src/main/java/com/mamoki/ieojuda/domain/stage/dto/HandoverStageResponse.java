package com.mamoki.ieojuda.domain.stage.dto;

import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStageStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.time.LocalDateTime;

// "단계 완료 / 대체 담당자" 화면 - 현재 단계 상태
public record HandoverStageResponse(
        @Schema(description = "단계 ID") Long stageId,
        @Schema(description = "단계 순서") Integer stageOrder,
        @Schema(description = "단계 상태", example = "SENT", allowableValues = {"PENDING", "READY", "SENT", "COMPLETED", "BOUNCED", "FALLBACK", "BLOCKED"}) String status,
        @Schema(description = "현재 담당자 이름") String recipientName,
        @Schema(description = "현재 담당자 이메일") String recipientEmail,
        @Schema(description = "발송 시각") LocalDateTime sentAt,
        @Schema(description = "완료 시각 (없으면 null)") LocalDateTime confirmedAt,
        @Schema(description = "최대 대기 시간까지 남은 분 (발송 전이거나 이미 끝났으면 null)") Long remainingMinutes
) {
    public static HandoverStageResponse from(HandoverStage stage) {
        return new HandoverStageResponse(
                stage.getStageId(),
                stage.getStageOrder(),
                stage.getStatus().name(),
                stage.getRecipient().getName(),
                stage.getRecipient().getEmail(),
                stage.getSentAt(),
                stage.getConfirmedAt(),
                computeRemainingMinutes(stage)
        );
    }

    private static Long computeRemainingMinutes(HandoverStage stage) {
        if (stage.getSentAt() == null || stage.getStatus() == HandoverStageStatus.COMPLETED) {
            return null;
        }
        Integer maxWaitHours = stage.getRecipient().getMaxWaitHours();
        if (maxWaitHours == null) {
            return null;
        }
        LocalDateTime deadline = stage.getSentAt().plusHours(maxWaitHours);
        return Duration.between(LocalDateTime.now(), deadline).toMinutes();
    }
}
