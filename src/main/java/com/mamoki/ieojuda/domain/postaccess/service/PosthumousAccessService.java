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
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.contract.EmailSendResult;
import com.mamoki.ieojuda.global.email.sender.EmailSender;
import com.mamoki.ieojuda.global.email.template.EmailBuilder;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.email.token.TokenValidator;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.ratelimit.PublicLinkAuditor;
import com.mamoki.ieojuda.global.ratelimit.TokenLookupGuard;
import com.mamoki.ieojuda.global.util.EmailMasker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

// 명세서 "사후 인계 이메일" - 만료 링크 검증과 별도 이메일 OTP 확인을 통과해야 역할별 패키지에 접근할 수 있다.
// 같은 이메일 계정에 의존하는 2단계 확인이며, 엄밀한 다중요소 인증(MFA)은 아니다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PosthumousAccessService {

    private static final int OTP_TTL_MINUTES = 10;
    private static final int OTP_RESEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final int SESSION_TTL_MINUTES = 60;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AccessTokenRepository accessTokenRepository;
    private final EmailLogRepository emailLogRepository;
    private final EmailSender emailSender;
    private final AppProperties appProperties;
    private final TokenLookupGuard tokenLookupGuard;
    private final PublicLinkAuditor publicLinkAuditor;

    // ① 링크 검증 - 인증 전이므로 인계 내용은 어떤 필드에도 포함하지 않는다
    @Transactional
    public PosthumousAccessResponse getAccess(String plainToken) {
        AccessToken accessToken = findByToken(plainToken);
        checkUsable(accessToken);
        return PosthumousAccessResponse.of(accessToken, appProperties.getContactEmail());
    }

    // ② OTP 발송 - 별도 이메일로 6자리 코드 발송, 해시만 저장
    @Transactional
    public OtpSendResponse sendOtp(String plainToken) {
        AccessToken accessToken = findByToken(plainToken);
        checkUsable(accessToken);
        checkAttemptsRemaining(accessToken);
        checkResendCooldown(accessToken);

        String otpCode = generateOtpCode();
        accessToken.recordOtpSend(TokenProvider.hashToken(otpCode));

        Recipient recipient = accessToken.getHandoverStage().getRecipient();
        LocalDateTime otpExpiresAt = accessToken.getOtpSentAt().plusMinutes(OTP_TTL_MINUTES);

        EmailContent content = EmailBuilder.buildOtp(
                otpCode,
                otpExpiresAt.atZone(ZoneId.systemDefault()),
                appProperties.getContactEmail()
        );
        EmailSendResult result = emailSender.send(recipient.getEmail(), content);

        emailLogRepository.save(EmailLog.builder()
                .plan(accessToken.getHandoverStage().getPlan())
                .handoverStage(accessToken.getHandoverStage())
                .emailType(EmailType.OTP)
                .recipientEmail(recipient.getEmail())
                .messageId(result.messageId())
                .build());

        return new OtpSendResponse(
                EmailMasker.mask(recipient.getEmail()),
                otpExpiresAt,
                accessToken.getOtpSentAt().plusSeconds(OTP_RESEND_COOLDOWN_SECONDS)
        );
    }

    // ③ OTP 확인 - 성공 시 역할별 패키지 조회에 쓸 열람 세션 발급
    @Transactional
    public OtpVerifyResponse verify(String plainToken, OtpVerifyRequest request) {
        AccessToken accessToken = findByToken(plainToken);
        checkUsable(accessToken);
        checkAttemptsRemaining(accessToken);
        checkOtpSent(accessToken);
        checkOtpNotExpired(accessToken);

        if (!accessToken.getOtpCodeHash().equals(TokenProvider.hashToken(request.otpCode()))) {
            accessToken.incrementAttempt();
            publicLinkAuditor.recordStateFailure(ErrorCode.OTP_VERIFICATION_FAILED);
            throw new CustomException(ErrorCode.OTP_VERIFICATION_FAILED);
        }

        String sessionToken = TokenProvider.generatePlainToken();
        LocalDateTime sessionExpiresAt = LocalDateTime.now().plusMinutes(SESSION_TTL_MINUTES);
        accessToken.verify(TokenProvider.hashToken(sessionToken), sessionExpiresAt);

        return new OtpVerifyResponse(sessionToken, sessionExpiresAt);
    }

    // 발송 단계가 활성화될 때 호출 - AccessToken을 발급하고 평문 토큰을 반환한다.
    // (링크 문구·주소 교체는 별도 이슈에서 이 메서드를 호출하도록 연결한다)
    @Transactional
    public String issueAccessToken(HandoverStage stage) {
        String plainToken = TokenProvider.generatePlainToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(appProperties.getInviteTokenTtlHours());
        accessTokenRepository.save(AccessToken.builder()
                .handoverStage(stage)
                .tokenHash(TokenProvider.hashToken(plainToken))
                .expiresAt(expiresAt)
                .build());
        return plainToken;
    }

    private AccessToken findByToken(String plainToken) {
        return tokenLookupGuard.resolve(plainToken,
                () -> accessTokenRepository.findByTokenHash(TokenProvider.hashToken(plainToken)));
    }

    // 만료되었거나 이미 인증까지 끝나 소진된(1회성) 링크를 차단
    private void checkUsable(AccessToken accessToken) {
        Instant expiresAt = accessToken.getExpiresAt() == null
                ? null
                : accessToken.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant();
        if (TokenValidator.isExpired(expiresAt, Instant.now())) {
            publicLinkAuditor.recordStateFailure(ErrorCode.ACCESS_LINK_EXPIRED);
            throw new CustomException(ErrorCode.ACCESS_LINK_EXPIRED);
        }
        if (!TokenValidator.isUsable(Boolean.TRUE.equals(accessToken.getUsed()))) {
            publicLinkAuditor.recordStateFailure(ErrorCode.ACCESS_LINK_ALREADY_USED);
            throw new CustomException(ErrorCode.ACCESS_LINK_ALREADY_USED);
        }
    }

    // OTP 검증 실패 5회를 넘긴 링크는 코드가 맞아도 더 이상 진행할 수 없도록 전체 차단
    private void checkAttemptsRemaining(AccessToken accessToken) {
        if (!TokenValidator.isUsable(accessToken.getAttemptCount(), MAX_OTP_ATTEMPTS)) {
            publicLinkAuditor.recordStateFailure(ErrorCode.TOKEN_TEMPORARILY_LOCKED);
            throw new CustomException(ErrorCode.TOKEN_TEMPORARILY_LOCKED);
        }
    }

    private void checkResendCooldown(AccessToken accessToken) {
        if (accessToken.getOtpSentAt() != null
                && accessToken.getOtpSentAt().plusSeconds(OTP_RESEND_COOLDOWN_SECONDS).isAfter(LocalDateTime.now())) {
            throw new CustomException(ErrorCode.TOO_MANY_REQUESTS);
        }
    }

    private void checkOtpSent(AccessToken accessToken) {
        if (accessToken.getOtpCodeHash() == null || accessToken.getOtpSentAt() == null) {
            throw new CustomException(ErrorCode.OTP_VERIFICATION_FAILED);
        }
    }

    private void checkOtpNotExpired(AccessToken accessToken) {
        LocalDateTime otpExpiresAt = accessToken.getOtpSentAt().plusMinutes(OTP_TTL_MINUTES);
        if (LocalDateTime.now().isAfter(otpExpiresAt)) {
            throw new CustomException(ErrorCode.OTP_VERIFICATION_FAILED);
        }
    }

    private String generateOtpCode() {
        int code = SECURE_RANDOM.nextInt(1_000_000);
        return String.format("%06d", code);
    }
}
