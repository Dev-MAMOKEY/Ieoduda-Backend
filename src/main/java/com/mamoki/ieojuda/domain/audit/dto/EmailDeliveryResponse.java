package com.mamoki.ieojuda.domain.audit.dto;

import com.mamoki.ieojuda.domain.audit.entity.EmailLog;
import com.mamoki.ieojuda.global.util.EmailMasker;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// "이메일 발송 감사" 화면 - 발송 건 하나. 본문은 애초에 EmailLog에 저장하지 않으므로 여기에도 없음(내용 비노출 원칙)
public record EmailDeliveryResponse(
        @Schema(description = "로그 ID") Long logId,
        @Schema(description = "수신자 이메일 (마스킹됨)") String recipientEmail,
        @Schema(description = "이메일 종류") String emailType,
        @Schema(description = "재시도 횟수") Integer retryCount,
        @Schema(description = "메시지 식별자") String messageId,
        @Schema(description = "발송 시각") LocalDateTime sentAt,
        @Schema(description = "열람 시각 (없으면 null)") LocalDateTime openedAt,
        @Schema(description = "반송 시각 (없으면 null)") LocalDateTime bouncedAt,
        @Schema(description = "취소 시각 (없으면 null)") LocalDateTime canceledAt
) {
    public static EmailDeliveryResponse from(EmailLog log) {
        return new EmailDeliveryResponse(
                log.getLogId(),
                EmailMasker.mask(log.getRecipientEmail()),
                log.getEmailType() == null ? null : log.getEmailType().name(),
                log.getRetryCount(),
                log.getMessageId(),
                log.getSentAt(),
                log.getOpenedAt(),
                log.getBouncedAt(),
                log.getCanceledAt()
        );
    }
}
