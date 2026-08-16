package com.mamoki.ieojuda.domain.postaccess.service;

import com.mamoki.ieojuda.domain.audit.entity.EmailLog;
import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.audit.repository.EmailLogRepository;
import com.mamoki.ieojuda.domain.postaccess.dto.OtpSendResponse;
import com.mamoki.ieojuda.domain.postaccess.dto.OtpVerifyRequest;
import com.mamoki.ieojuda.domain.postaccess.dto.OtpVerifyResponse;
import com.mamoki.ieojuda.domain.postaccess.dto.PosthumousAccessResponse;
import com.mamoki.ieojuda.domain.postaccess.entity.AccessToken;
import com.mamoki.ieojuda.domain.postaccess.repository.AccessTokenRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCaseStatus;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.contract.EmailSendResult;
import com.mamoki.ieojuda.global.email.sender.EmailSender;
import com.mamoki.ieojuda.global.email.template.EmailBuilder;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.email.token.TokenValidator;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

// 명세서 "사후 인계 이메일" 화면 - 역할 담당자가 1통차 링크로 진입해 2통차 OTP까지 확인한다 (로그인 불필요, 링크 토큰이 곧 인증)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PosthumousAccessService {

    private final AccessTokenRepository accessTokenRepository;
    private final EmailLogRepository emailLogRepository;
    private final EmailSender emailSender;
    private final PasswordEncoder passwordEncoder;
    private final OtpAttemptRecorder otpAttemptRecorder;
    private final AppProperties appProperties;

    public PosthumousAccessResponse verifyLink(String plainToken) {
        AccessToken token = findUsableToken(plainToken);
        return PosthumousAccessResponse.of(token, appProperties.getContactEmail());
    }

    // "인증번호 발송하기" / "코드 재발송" - 재발송 시 시도 횟수를 초기화한다(사용자 확정)
    @Transactional
    public OtpSendResponse sendOtp(String plainToken) {
        AccessToken token = findUsableToken(plainToken);
        Recipient recipient = token.getHandoverStage().getRecipient();

        String otpCode = TokenProvider.generateOtpCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(appProperties.getOtpTtlMinutes());
        token.issueOtp(passwordEncoder.encode(otpCode));

        EmailContent content = EmailBuilder.buildOtp(otpCode, expiresAt.atZone(ZoneId.systemDefault()), appProperties.getContactEmail());
        EmailSendResult result = emailSender.send(recipient.getEmail(), content);

        emailLogRepository.save(EmailLog.builder()
                .plan(token.getHandoverStage().getPlan())
                .handoverStage(token.getHandoverStage())
                .emailType(EmailType.OTP)
                .recipientEmail(recipient.getEmail())
                .messageId(result.messageId())
                .build());

        return OtpSendResponse.of(recipient.getEmail(), expiresAt);
    }

    // "인증 코드 입력" 제출 - 성공 시 링크를 소진하고 다음 화면 분기 정보를 반환한다
    @Transactional
    public OtpVerifyResponse verifyOtp(String plainToken, OtpVerifyRequest request) {
        AccessToken token = findUsableToken(plainToken);

        if (!TokenValidator.isUsable(token.getAttemptCount(), appProperties.getOtpMaxAttempts())) {
            throw new CustomException(ErrorCode.OTP_VERIFICATION_FAILED);
        }
        if (token.getOtpCodeHash() == null || token.getOtpSentAt() == null) {
            throw new CustomException(ErrorCode.OTP_VERIFICATION_FAILED);
        }
        Instant otpExpiresAt = token.getOtpSentAt().plusMinutes(appProperties.getOtpTtlMinutes())
                .atZone(ZoneId.systemDefault()).toInstant();
        if (TokenValidator.isExpired(otpExpiresAt, Instant.now())) {
            throw new CustomException(ErrorCode.OTP_VERIFICATION_FAILED);
        }
        if (!passwordEncoder.matches(request.otpCode(), token.getOtpCodeHash())) {
            otpAttemptRecorder.recordFailedAttempt(token);
            throw new CustomException(ErrorCode.OTP_VERIFICATION_FAILED);
        }

        token.verify();
        return OtpVerifyResponse.from(token);
    }

    private AccessToken findUsableToken(String plainToken) {
        AccessToken token = accessTokenRepository.findByTokenHash(TokenProvider.hashToken(plainToken))
                .orElseThrow(() -> new CustomException(ErrorCode.TOKEN_INVALID));

        Instant expiresAt = token.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant();
        if (TokenValidator.isExpired(expiresAt, Instant.now())) {
            throw new CustomException(ErrorCode.ACCESS_LINK_EXPIRED);
        }
        if (!TokenValidator.isUsable(Boolean.TRUE.equals(token.getUsed()))) {
            throw new CustomException(ErrorCode.ACCESS_LINK_ALREADY_USED);
        }

        ReleaseCase releaseCase = token.getHandoverStage().getReleaseCase();
        if (Boolean.TRUE.equals(releaseCase.getFrozen())) {
            throw new CustomException(ErrorCode.RELEASE_CASE_FROZEN);
        }
        if (releaseCase.getStatus() != ReleaseCaseStatus.RELEASING) {
            throw new CustomException(ErrorCode.DISPUTE_RAISED);
        }

        return token;
    }
}
