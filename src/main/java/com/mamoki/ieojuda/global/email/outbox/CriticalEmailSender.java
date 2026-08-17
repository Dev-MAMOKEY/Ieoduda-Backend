package com.mamoki.ieojuda.global.email.outbox;

import com.mamoki.ieojuda.domain.audit.entity.EmailLog;
import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.audit.repository.EmailLogRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.contract.EmailSendResult;
import com.mamoki.ieojuda.global.email.sender.EmailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 사건 생성·대기 시작처럼 "발송 성공을 확인해야만 다음 상태 전이를 허용"해야 하는 경고 메일 전용 발송 경로.
// EmailOutboxService는 "네트워크 호출 없이 DB 저장만 한다"는 게 명시된 불변식이라(EmailOutboxScheduler가
// 10분 주기로 비동기 발송) 재사용하지 않고, 이 컴포넌트가 EmailSender를 직접 동기 호출해 그 자리에서
// 성공/실패를 확정한다. 실패해도 EmailLog에는 FAILED로 정확히 기록되어 "발송 실패가 성공으로 기록되지
// 않는다"를 보장한다.
@Component
@RequiredArgsConstructor
public class CriticalEmailSender {

    private final EmailLogRepository emailLogRepository;
    private final EmailSender emailSender;

    @Transactional
    public boolean sendOrFail(Plan plan, EmailType emailType, String recipientEmail, EmailContent content) {
        EmailLog log = emailLogRepository.save(EmailLog.builder()
                .plan(plan)
                .handoverStage(null)
                .emailType(emailType)
                .recipientEmail(recipientEmail)
                .build());

        EmailSendResult result = emailSender.send(recipientEmail, content);
        if (result.success()) {
            log.markSent(result.messageId());
            return true;
        }

        log.markFailed(result.bounceType() + ":" + result.emailFaill());
        return false;
    }
}
