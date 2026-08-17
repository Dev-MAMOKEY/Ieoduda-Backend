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

    // 10분마다 - EvidenceDeletionScheduler/ReleaseCaseScheduler와 같은 주기
    @Scheduled(fixedRate = 600_000)
    @Transactional
    public void dispatchPending() {
        List<EmailOutbox> pending = emailOutboxRepository.findPendingForUpdateSkipLocked();

        for (EmailOutbox outbox : pending) {
            dispatchOne(outbox);
        }
    }

    private void dispatchOne(EmailOutbox outbox) {
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

    private String describeFailure(EmailSendResult result) {
        return result.bounceType() + ":" + result.emailFaill();
    }
}
