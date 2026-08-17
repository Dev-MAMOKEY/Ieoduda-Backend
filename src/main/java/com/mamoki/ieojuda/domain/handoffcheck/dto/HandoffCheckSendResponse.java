package com.mamoki.ieojuda.domain.handoffcheck.dto;

import com.mamoki.ieojuda.domain.handoffcheck.entity.HandoffCheck;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// "선택형 생전 인계 점검" 화면 - 점검 발송 결과
public record HandoffCheckSendResponse(
        @Schema(description = "발송된 점검 ID") Long checkId,
        @Schema(description = "발송 시각") LocalDateTime sentAt,
        @Schema(description = "점검 메일을 받은 담당자 수") int targetCount
) {
    public static HandoffCheckSendResponse of(HandoffCheck handoffCheck, int targetCount) {
        return new HandoffCheckSendResponse(handoffCheck.getCheckId(), handoffCheck.getSentAt(), targetCount);
    }
}
