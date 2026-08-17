package com.mamoki.ieojuda.global.email.sender;

import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.contract.EmailSendResult;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// issue #51 - Resilience4j 재시도/회로차단이 GmailSender의 EmailSender 계약(never throws)을 지키면서
// 짧은 재시도로 일시 장애를 흡수하는지 검증한다.
class GmailSenderTest {

    private JavaMailSender javaMailSender;
    private GmailSender gmailSender;

    @BeforeEach
    void setUp() {
        javaMailSender = mock(JavaMailSender.class);
        when(javaMailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));

        RetryRegistry retryRegistry = RetryRegistry.of(Map.of("emailSend",
                RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(1)).build()));
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(Map.of("emailSend",
                CircuitBreakerConfig.ofDefaults()));
        gmailSender = new GmailSender(javaMailSender, retryRegistry, circuitBreakerRegistry);
    }

    @Test
    void send_whenFirstAttemptsFailThenSucceed_retriesAndReturnsSuccess() {
        doThrow(new MailSendException("boom"))
                .doThrow(new MailSendException("boom"))
                .doNothing()
                .when(javaMailSender).send(any(MimeMessage.class));

        EmailSendResult result = gmailSender.send("to@example.com", new EmailContent("제목", "본문"));

        assertThat(result.success()).isTrue();
        verify(javaMailSender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    void send_whenAllAttemptsFail_returnsFailureClassifiedByFailureAnalyzer() {
        doThrow(new MailSendException("boom")).when(javaMailSender).send(any(MimeMessage.class));

        EmailSendResult result = gmailSender.send("to@example.com", new EmailContent("제목", "본문"));

        assertThat(result.success()).isFalse();
        verify(javaMailSender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    void send_whenSucceedsOnFirstTry_sendsExactlyOnce() {
        EmailSendResult result = gmailSender.send("to@example.com", new EmailContent("제목", "본문"));

        assertThat(result.success()).isTrue();
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }
}
