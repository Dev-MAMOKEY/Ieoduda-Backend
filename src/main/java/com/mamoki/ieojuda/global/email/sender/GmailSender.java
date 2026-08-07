package com.mamoki.ieojuda.global.email.sender;

import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.contract.EmailSendResult;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GmailSender implements EmailSender {

    private final JavaMailSender javaMailSender;

    @Override
    public EmailSendResult send(String toEmail, EmailContent content) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject(content.subject());
            helper.setText(content.body(), false);

            javaMailSender.send(message);

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