package com.mamoki.ieojuda.global.email.outbox;

import com.mamoki.ieojuda.domain.audit.entity.AdminActionType;
import com.mamoki.ieojuda.domain.audit.entity.EmailLog;
import com.mamoki.ieojuda.domain.audit.service.AdminActionAuditService;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.BounceType;
import com.mamoki.ieojuda.global.email.contract.EmailFaill;
import com.mamoki.ieojuda.global.email.contract.EmailSendResult;
import com.mamoki.ieojuda.global.email.sender.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// issue #51 - "SMTP 발송 실패가 성공으로 기록되지 않는다"와 "중복 워커 실행에도 한 번만 처리된다"를
// 직접 검증한다. EvidenceDeletionSchedulerTest와 동일한 구조(행 단위 격리, 배치 부분 실패).
class EmailOutboxSchedulerTest {

    private EmailOutboxRepository emailOutboxRepository;
    private EmailSender emailSender;
    private AppProperties appProperties;
    private AdminActionAuditService adminActionAuditService;
    private EmailOutboxScheduler scheduler;

    @BeforeEach
    void setUp() {
        emailOutboxRepository = mock(EmailOutboxRepository.class);
        emailSender = mock(EmailSender.class);
        appProperties = mock(AppProperties.class);
        adminActionAuditService = mock(AdminActionAuditService.class);
        when(appProperties.getEmailOutboxMaxAttempts()).thenReturn(3);
        scheduler = new EmailOutboxScheduler(emailOutboxRepository, emailSender, appProperties, adminActionAuditService);
    }

    @Test
    void dispatchPending_whenNoneAreDue_doesNothing() {
        when(emailOutboxRepository.findPendingForUpdateSkipLocked()).thenReturn(List.of());

        scheduler.dispatchPending();

        verifyNoInteractions(emailSender, adminActionAuditService);
    }

    @Test
    void dispatchPending_whenSendSucceeds_marksLogAndOutboxSentAndDispatchesStage() {
        EmailLog log = mock(EmailLog.class);
        HandoverStage stage = mock(HandoverStage.class);
        EmailOutbox outbox = outboxOf(log, stage);
        when(emailOutboxRepository.findPendingForUpdateSkipLocked()).thenReturn(List.of(outbox));
        when(emailSender.send(eq("a@b.com"), any())).thenReturn(EmailSendResult.success("msg-1"));

        scheduler.dispatchPending();

        verify(log).markSent("msg-1");
        verify(outbox).markSent();
        verify(stage).send();
        verifyNoInteractions(adminActionAuditService);
    }

    @Test
    void dispatchPending_whenSendSucceeds_withoutStage_doesNotTouchStage() {
        EmailLog log = mock(EmailLog.class);
        EmailOutbox outbox = outboxOf(log, null);
        when(emailOutboxRepository.findPendingForUpdateSkipLocked()).thenReturn(List.of(outbox));
        when(emailSender.send(eq("a@b.com"), any())).thenReturn(EmailSendResult.success("msg-1"));

        scheduler.dispatchPending();

        verify(log).markSent("msg-1");
        verify(outbox).markSent();
    }

    @Test
    void dispatchPending_whenSendFailsTemporarilyUnderMaxAttempts_recordsFailedAttemptAndStaysPending() {
        EmailLog log = mock(EmailLog.class);
        EmailOutbox outbox = outboxOf(log, null);
        when(outbox.getAttemptCount()).thenReturn(0);
        when(emailOutboxRepository.findPendingForUpdateSkipLocked()).thenReturn(List.of(outbox));
        when(emailSender.send(eq("a@b.com"), any()))
                .thenReturn(EmailSendResult.failure(BounceType.TEMPORARY, EmailFaill.CONNECTION_TIMEOUT));

        scheduler.dispatchPending();

        verify(log).markFailed(anyString());
        verify(outbox).recordFailedAttempt(anyString());
        verify(outbox, never()).giveUp(anyString());
        verifyNoInteractions(adminActionAuditService);
    }

    @Test
    void dispatchPending_whenSendFailsAtMaxAttempts_givesUpAndAudits() {
        EmailLog log = mock(EmailLog.class);
        EmailOutbox outbox = outboxOf(log, null);
        when(outbox.getOutboxId()).thenReturn(UUID.randomUUID());
        when(outbox.getAttemptCount()).thenReturn(3); // 이미 maxAttempts(3)만큼 시도함
        when(emailOutboxRepository.findPendingForUpdateSkipLocked()).thenReturn(List.of(outbox));
        when(emailSender.send(eq("a@b.com"), any()))
                .thenReturn(EmailSendResult.failure(BounceType.TEMPORARY, EmailFaill.CONNECTION_TIMEOUT));

        scheduler.dispatchPending();

        verify(log).markFailed(anyString());
        verify(outbox).giveUp(anyString());
        verify(outbox, never()).recordFailedAttempt(anyString());
        verify(adminActionAuditService)
                .recordSystem(eq(AdminActionType.EMAIL_OUTBOX_DISPATCH_FAILED), any(UUID.class), eq(false), anyString());
    }

    @Test
    void dispatchPending_whenBounceIsPermanent_givesUpImmediatelyRegardlessOfAttemptCount() {
        EmailLog log = mock(EmailLog.class);
        EmailOutbox outbox = outboxOf(log, null);
        when(outbox.getOutboxId()).thenReturn(UUID.randomUUID());
        when(outbox.getAttemptCount()).thenReturn(0); // 첫 시도인데도
        when(emailOutboxRepository.findPendingForUpdateSkipLocked()).thenReturn(List.of(outbox));
        when(emailSender.send(eq("a@b.com"), any()))
                .thenReturn(EmailSendResult.failure(BounceType.PERMANENT, EmailFaill.INVALID_ADDRESS_FORMAT));

        scheduler.dispatchPending();

        verify(outbox).giveUp(anyString());
        verify(outbox, never()).recordFailedAttempt(anyString());
    }

    @Test
    void dispatchPending_whenOneOfSeveralFails_theOthersStillGetSent() {
        EmailLog failingLog = mock(EmailLog.class);
        EmailOutbox failing = outboxOf(failingLog, null);
        when(failing.getRecipientEmail()).thenReturn("fail@b.com");
        when(failing.getAttemptCount()).thenReturn(0);

        EmailLog succeedingLog = mock(EmailLog.class);
        EmailOutbox succeeding = outboxOf(succeedingLog, null);
        when(succeeding.getRecipientEmail()).thenReturn("ok@b.com");

        when(emailOutboxRepository.findPendingForUpdateSkipLocked()).thenReturn(List.of(failing, succeeding));
        when(emailSender.send(eq("fail@b.com"), any()))
                .thenReturn(EmailSendResult.failure(BounceType.TEMPORARY, EmailFaill.CONNECTION_TIMEOUT));
        when(emailSender.send(eq("ok@b.com"), any())).thenReturn(EmailSendResult.success("msg-2"));

        scheduler.dispatchPending();

        verify(failing).recordFailedAttempt(anyString());
        verify(succeeding).markSent();
        verify(succeedingLog).markSent("msg-2");
    }

    // 재처리 멱등성: 이미 SENT로 전이된 행은 두 번째 스케줄러 실행에서 더는 조회되지 않으므로
    // (findPendingForUpdateSkipLocked의 status='PENDING' 조건), 같은 행에 대해 emailSender.send가
    // 두 번 호출되는 일은 없다.
    @Test
    void dispatchPending_calledTwiceWithSameRowOnlyOnFirstRun_sendsExactlyOnce() {
        EmailLog log = mock(EmailLog.class);
        EmailOutbox outbox = outboxOf(log, null);
        when(emailOutboxRepository.findPendingForUpdateSkipLocked())
                .thenReturn(List.of(outbox))
                .thenReturn(List.of()); // 두 번째 주기에는 SENT로 전이되어 더는 조회되지 않음
        when(emailSender.send(eq("a@b.com"), any())).thenReturn(EmailSendResult.success("msg-1"));

        scheduler.dispatchPending();
        scheduler.dispatchPending();

        verify(emailSender, org.mockito.Mockito.times(1)).send(eq("a@b.com"), any());
    }

    private EmailOutbox outboxOf(EmailLog log, HandoverStage stage) {
        EmailOutbox outbox = mock(EmailOutbox.class);
        when(outbox.getEmailLog()).thenReturn(log);
        when(outbox.getHandoverStage()).thenReturn(stage);
        when(outbox.getRecipientEmail()).thenReturn("a@b.com");
        when(outbox.getSubject()).thenReturn("subject");
        when(outbox.getBody()).thenReturn("body");
        return outbox;
    }
}
