package com.mamoki.ieojuda.global.email.outbox;

import com.mamoki.ieojuda.domain.audit.entity.EmailLog;
import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.audit.repository.EmailLogRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// issue #51 - 트랜잭셔널 아웃박스의 유일한 쓰기 지점. 여기서 EmailLog/EmailOutbox 연결이 잘못되면
// 워커가 엉뚱한 로그를 갱신하거나, 감사 화면에 발송 이력이 아예 안 남는 문제로 이어진다.
class EmailOutboxServiceTest {

    private EmailLogRepository emailLogRepository;
    private EmailOutboxRepository emailOutboxRepository;
    private EmailOutboxService emailOutboxService;

    @BeforeEach
    void setUp() {
        emailLogRepository = mock(EmailLogRepository.class);
        emailOutboxRepository = mock(EmailOutboxRepository.class);
        emailOutboxService = new EmailOutboxService(emailLogRepository, emailOutboxRepository);

        when(emailLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(emailOutboxRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void enqueue_savesRequestedEmailLogAndLinkedOutboxRowInSameCall() {
        Plan plan = mock(Plan.class);
        HandoverStage stage = mock(HandoverStage.class);
        EmailContent content = new EmailContent("제목", "본문");

        emailOutboxService.enqueue(plan, stage, EmailType.POSTHUMOUS_HANDOFF_LINK, "a@b.com", content);

        ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(logCaptor.capture());
        EmailLog savedLog = logCaptor.getValue();

        ArgumentCaptor<EmailOutbox> outboxCaptor = ArgumentCaptor.forClass(EmailOutbox.class);
        verify(emailOutboxRepository).save(outboxCaptor.capture());
        EmailOutbox savedOutbox = outboxCaptor.getValue();

        assertThat(savedOutbox.getEmailLog()).isSameAs(savedLog);
        assertThat(savedOutbox.getHandoverStage()).isSameAs(stage);
        assertThat(savedOutbox.getRecipientEmail()).isEqualTo("a@b.com");
        assertThat(savedOutbox.getSubject()).isEqualTo("제목");
        assertThat(savedOutbox.getBody()).isEqualTo("본문");
        assertThat(savedOutbox.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
    }

    @Test
    void enqueueRetry_requestsRetryOnExistingLog_andAddsNewOutboxRowReferencingIt() {
        EmailLog existingLog = mock(EmailLog.class);
        when(existingLog.getHandoverStage()).thenReturn(null);
        when(existingLog.getRecipientEmail()).thenReturn("retry@b.com");
        EmailContent content = new EmailContent("재발송 제목", "재발송 본문");

        emailOutboxService.enqueueRetry(existingLog, content);

        verify(existingLog).recordRetryRequested();
        // enqueueRetry는 새 EmailLog를 만들지 않는다 - 기존 로그를 재사용해 이력을 보존한다
        verify(emailLogRepository, org.mockito.Mockito.never()).save(any());

        ArgumentCaptor<EmailOutbox> outboxCaptor = ArgumentCaptor.forClass(EmailOutbox.class);
        verify(emailOutboxRepository).save(outboxCaptor.capture());
        EmailOutbox savedOutbox = outboxCaptor.getValue();

        assertThat(savedOutbox.getEmailLog()).isSameAs(existingLog);
        assertThat(savedOutbox.getRecipientEmail()).isEqualTo("retry@b.com");
        assertThat(savedOutbox.getSubject()).isEqualTo("재발송 제목");
    }
}
