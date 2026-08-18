package com.mamoki.ieojuda.global.email.sender;

import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.contract.EmailSendResult;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

// issue #51 - EmailOutboxScheduler의 한 시도 안에서 발생하는 일시적 SMTP 장애를 짧게 흡수한다
// (긴 호흡의 재전송은 EmailOutbox.attemptCount가 스케줄러 주기 단위로 담당 - 서로 다른 레이어라 이중 재시도가 아니다).
@Slf4j
@Component
public class GmailSender implements EmailSender {

    private final JavaMailSender javaMailSender;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final String fromAddress;

    public GmailSender(JavaMailSender javaMailSender, RetryRegistry retryRegistry, CircuitBreakerRegistry circuitBreakerRegistry,
                        @Value("${spring.mail.username}") String fromAddress) {
        this.javaMailSender = javaMailSender;
        this.retry = retryRegistry.retry("emailSend");
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("emailSend");
        this.fromAddress = fromAddress;
    }

    @Override
    public EmailSendResult send(String toEmail, EmailContent content) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(content.subject());
            helper.setText(content.body(), false);

            Supplier<Void> sendOnce = Retry.decorateSupplier(retry, () -> {
                javaMailSender.send(message);
                return null;
            });
            CircuitBreaker.decorateSupplier(circuitBreaker, sendOnce).get();

            //이 메일 한 통의 고유 식별자 (어떤 발송 건인지)
            String messageId = message.getMessageID() != null // 이메일 발송 이력 추적을 위해 (phase8 고려)
                    ? message.getMessageID()
                    : UUID.randomUUID().toString(); // 가끔 null 값을 반환 하는 경우가 있어 UUID로 임의 번호 부여

            return EmailSendResult.success(messageId);

        } catch (Exception e) {
            log.error("[Email Send Failed] to={}, cause={}", toEmail, e.getMessage(), e);
            FailureAnalyzer.Result result = FailureAnalyzer.analyze(e);
            return EmailSendResult.failure(result.bounceType(), result.emailFaill());
        }
    }
}