package com.mamoki.ieojuda.domain.audit.entity;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// issue #51 - EmailLog가 "SENT"로 기록되는 경로는 markSent() 하나뿐이어야 한다는 것이 이번 리팩터의 핵심.
class EmailLogTest {

    @Test
    void construct_startsAsRequestedWithoutSentAt() {
        EmailLog log = EmailLog.builder()
                .plan(mock(Plan.class))
                .handoverStage(null)
                .emailType(EmailType.POSTHUMOUS_HANDOFF_LINK)
                .recipientEmail("test@example.com")
                .build();

        assertThat(log.getStatus()).isEqualTo(EmailDeliveryStatus.REQUESTED);
        assertThat(log.getSentAt()).isNull();
        assertThat(log.getRequestedAt()).isNotNull();
        assertThat(log.getRetryCount()).isZero();
    }

    @Test
    void markSent_setsSentStatusAndSentAtAndMessageId() {
        EmailLog log = newRequestedLog();

        log.markSent("message-id-1");

        assertThat(log.getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(log.getMessageId()).isEqualTo("message-id-1");
        assertThat(log.getSentAt()).isNotNull();
    }

    @Test
    void markFailed_setsFailedStatusAndReason_neverTouchesSentAt() {
        EmailLog log = newRequestedLog();

        log.markFailed("TEMPORARY:CONNECTION_TIMEOUT");

        assertThat(log.getStatus()).isEqualTo(EmailDeliveryStatus.FAILED);
        assertThat(log.getFailureReason()).isEqualTo("TEMPORARY:CONNECTION_TIMEOUT");
        assertThat(log.getSentAt()).isNull();
    }

    @Test
    void recordRetryRequested_incrementsRetryCountAndResetsToRequested() {
        EmailLog log = newRequestedLog();
        log.markFailed("TEMPORARY:CONNECTION_TIMEOUT");

        log.recordRetryRequested();

        assertThat(log.getRetryCount()).isEqualTo(1);
        assertThat(log.getStatus()).isEqualTo(EmailDeliveryStatus.REQUESTED);
        assertThat(log.getFailureReason()).isNull();
    }

    private EmailLog newRequestedLog() {
        return EmailLog.builder()
                .plan(mock(Plan.class))
                .handoverStage(null)
                .emailType(EmailType.POSTHUMOUS_HANDOFF_LINK)
                .recipientEmail("test@example.com")
                .build();
    }
}
