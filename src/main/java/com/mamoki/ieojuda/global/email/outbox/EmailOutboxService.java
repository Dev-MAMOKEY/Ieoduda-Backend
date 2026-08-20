package com.mamoki.ieojuda.global.email.outbox;

import com.mamoki.ieojuda.domain.audit.entity.EmailLog;
import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.audit.repository.EmailLogRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// issue #51 - SMTP 발송을 DB 트랜잭션 밖으로 분리하는 트랜잭셔널 아웃박스의 유일한 쓰기 지점.
// 여기는 순수 DB 저장만 하고 네트워크 호출은 하지 않는다 - 호출부의 기존 @Transactional 경계에
// 그대로 참여해서, 비즈니스 데이터와 발송 요청이 같은 트랜잭션에 원자적으로 커밋되게 한다
// (IdempotencyGuard와 같은 이유로 독립 트랜잭션을 쓰지 않는다). 실제 발송은 EmailOutboxScheduler가 담당.
@Component
@RequiredArgsConstructor
public class EmailOutboxService {

    private final EmailLogRepository emailLogRepository;
    private final EmailOutboxRepository emailOutboxRepository;

    // 신규 발송 요청 - 새 EmailLog(REQUESTED)와 EmailOutbox 행을 같은 트랜잭션에 커밋한다.
    public void enqueue(Plan plan, HandoverStage handoverStage, EmailType emailType,
                         String recipientEmail, EmailContent content) {
        EmailLog log = emailLogRepository.save(EmailLog.builder()
                .plan(plan)
                .handoverStage(handoverStage)
                .emailType(emailType)
                .recipientEmail(recipientEmail)
                .build());

        emailOutboxRepository.save(EmailOutbox.builder()
                .emailLog(log)
                .handoverStage(handoverStage)
                .recipientEmail(recipientEmail)
                .subject(content.subject())
                .body(content.body())
                .build());
    }

    // "이메일 발송 감사" 화면 "재시도 정책 실행하기" - 기존 EmailLog(이력 보존)를 REQUESTED로 되돌리고
    // 새 EmailOutbox 행만 추가한다.
    public void enqueueRetry(EmailLog existingLog, EmailContent content) {
        existingLog.recordRetryRequested();

        emailOutboxRepository.save(EmailOutbox.builder()
                .emailLog(existingLog)
                .handoverStage(existingLog.getHandoverStage())
                .recipientEmail(existingLog.getRecipientEmail())
                .subject(content.subject())
                .body(content.body())
                .build());
    }
}
