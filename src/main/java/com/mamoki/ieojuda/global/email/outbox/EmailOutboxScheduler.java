package com.mamoki.ieojuda.global.email.outbox;

import com.mamoki.ieojuda.domain.audit.entity.AdminActionType;
import com.mamoki.ieojuda.domain.audit.entity.EmailLog;
import com.mamoki.ieojuda.domain.audit.service.AdminActionAuditService;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.contract.EmailSendResult;
import com.mamoki.ieojuda.global.email.sender.EmailSender;
import com.mamoki.ieojuda.global.email.sender.RetryPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// issue #51 - 아웃박스에 쌓인 이메일을 실제로 발송하는 워커.
// EvidenceDeletionScheduler와 동일한 이유로 FOR UPDATE SKIP LOCKED를 써서 다중 인스턴스가 같은 행을
// 중복 처리하지 않게 하고(정확히 한 번 처리), 한 건의 실패가 다른 건의 성공을 롤백시키지 않도록
// 행 단위로 예외를 격리한다. status='PENDING' 조건 자체가 재시도 가드다.
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailOutboxScheduler {

    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailSender emailSender;
    private final AppProperties appProperties;
    private final AdminActionAuditService adminActionAuditService;

    // 발송 주기 - app.email.outbox.dispatch-interval-ms (issue #51)
    @Scheduled(fixedRateString = "${app.email.outbox.dispatch-interval-ms}")
    @Transactional
    public void dispatchPending() {
        List<EmailOutbox> pending = emailOutboxRepository.findPendingForUpdateSkipLocked();

        for (EmailOutbox outbox : pending) {
            dispatchOne(outbox);
        }
    }

    // 이 메서드 밖으로 예외가 새어나가면 dispatchPending()의 트랜잭션 전체가 롤백돼, 같은 배치에서
    // 이미 성공 처리된 다른 행의 markSent()까지 전부 취소된다(버그 회귀 방지 - 발송 후 부수 효과인
    // stage.send()가 실패하는 경우 이 문제로 이미 발송된 메일이 무한 재전송됐었다). EvidenceDeletionScheduler와
    // 동일하게, 발송 자체는 성공했는데 그 이후 처리에서 예외가 나는 경우까지 넓게 잡아 이 건만 격리한다.
    private void dispatchOne(EmailOutbox outbox) {
        try {
            dispatchOneUnsafe(outbox);
        } catch (Exception e) {
            String reason = truncate(e.getMessage());
            log.error("[Email Outbox Dispatch] 예상치 못한 예외 - 이 건만 건너뛴다. outboxId={}, cause={}",
                    outbox.getOutboxId(), e.getMessage(), e);
            adminActionAuditService.recordSystem(
                    AdminActionType.EMAIL_OUTBOX_DISPATCH_FAILED, outbox.getOutboxId(), false, reason);
        }
    }

    private void dispatchOneUnsafe(EmailOutbox outbox) {
        EmailContent content = new EmailContent(outbox.getSubject(), outbox.getBody());
        EmailSendResult result = emailSender.send(outbox.getRecipientEmail(), content);
        EmailLog emailLog = outbox.getEmailLog();

        if (result.success()) {
            emailLog.markSent(result.messageId());
            outbox.markSent();
            HandoverStage stage = outbox.getHandoverStage();
            if (stage != null) {
                stage.send();
            }
            return;
        }

        String reason = describeFailure(result);
        emailLog.markFailed(reason);
        if (RetryPolicy.canRetry(result.bounceType(), outbox.getAttemptCount(), appProperties.getEmailOutboxMaxAttempts())) {
            outbox.recordFailedAttempt(reason);
        } else {
            outbox.giveUp(reason);
            log.error("[Email Outbox Dispatch Failed] outboxId={}, recipient={}, cause={}",
                    outbox.getOutboxId(), outbox.getRecipientEmail(), reason);
            adminActionAuditService.recordSystem(
                    AdminActionType.EMAIL_OUTBOX_DISPATCH_FAILED, outbox.getOutboxId(), false, reason);
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private String describeFailure(EmailSendResult result) {
        return result.bounceType() + ":" + result.emailFaill();
    }
}
