package com.mamoki.ieojuda.domain.postaccess.service;

import java.util.UUID;

import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.postaccess.dto.OtpSendResponse;
import com.mamoki.ieojuda.domain.postaccess.dto.OtpVerifyRequest;
import com.mamoki.ieojuda.domain.postaccess.dto.OtpVerifyResponse;
import com.mamoki.ieojuda.domain.postaccess.dto.PosthumousAccessResponse;
import com.mamoki.ieojuda.domain.postaccess.entity.AccessToken;
import com.mamoki.ieojuda.domain.postaccess.repository.AccessTokenRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStageStatus;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxService;
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
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AccessTokenRepository accessTokenRepository;
    private final EmailOutboxService emailOutboxService;
    private final AppProperties appProperties;
    private final TokenLookupGuard tokenLookupGuard;
    private final PublicLinkAuditor publicLinkAuditor;
    private final OtpAttemptRecorder otpAttemptRecorder;

    // ① 링크 검증 - 인증 전이므로 인계 내용은 어떤 필드에도 포함하지 않는다
    @Transactional
    public PosthumousAccessResponse getAccess(String plainToken) {
        AccessToken accessToken = findByToken(plainToken);
        checkUsable(accessToken);
        return PosthumousAccessResponse.of(accessToken, appProperties.getContactEmail());
    }

    // ② OTP 발송 - 별도 이메일로 4자리 코드 발송, 해시만 저장
    @Transactional
    public OtpSendResponse sendOtp(String plainToken) {
        AccessToken accessToken = findByTokenForUpdate(plainToken);
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
        // 실제 SMTP 발송은 EmailOutboxScheduler가 담당한다 (issue #51 - 발송 실패가 성공으로 기록되지 않도록).
        // handoverStage는 여기서 null로 넘긴다 - sendOtp()에 도달하려면 checkRoleConsistent()가 이미 이
        // 단계를 SENT로 확인했으므로, 발송 성공 시 스케줄러가 stage.send()를 다시 호출하면
        // (PENDING|READY|FALLBACK -> SENT만 허용) HANDOVER_STAGE_INVALID_TRANSITION이 터진다.
        // 그 예외가 dispatchPending()의 트랜잭션 전체를 롤백시켜 outbox.markSent()까지 취소되고,
        // 같은 행이 PENDING으로 남아 다음 주기마다 같은 코드로 무한 재전송되던 버그였다.
        emailOutboxService.enqueue(accessToken.getHandoverStage().getPlan(), null,
                EmailType.OTP, recipient.getEmail(), content);

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
            // 이 트랜잭션은 아래 예외로 곧 롤백되므로, 시도 횟수는 별도 트랜잭션(REQUIRES_NEW)으로 먼저 커밋한다.
            otpAttemptRecorder.recordFailure(accessToken.getTokenId());
            publicLinkAuditor.recordStateFailure(ErrorCode.OTP_VERIFICATION_FAILED);
            throw new CustomException(ErrorCode.OTP_VERIFICATION_FAILED);
        }

        // 새 컬럼을 두지 않고 같은 원본 토큰을 그대로 열람 세션 식별자로 쓴다 -
        // 이후 조회는 verifiedAt(세션 시작 시각) 기준으로 60분 이내인지만 확인하면 된다.
        accessToken.verify();
        LocalDateTime sessionExpiresAt = accessToken.getVerifiedAt().plusMinutes(AccessToken.SESSION_TTL_MINUTES);

        return new OtpVerifyResponse(plainToken, sessionExpiresAt);
    }

    private AccessToken findByToken(String plainToken) {
        return tokenLookupGuard.resolve(plainToken,
                () -> accessTokenRepository.findByTokenHash(TokenProvider.hashToken(plainToken)));
    }

    // OTP 발송 - 쿨다운 체크·발송 시각 갱신을 같은 잠긴 행 위에서 원자적으로 수행하기 위해 잠금 조회를 쓴다.
    private AccessToken findByTokenForUpdate(String plainToken) {
        return tokenLookupGuard.resolve(plainToken,
                () -> accessTokenRepository.findByTokenHashForUpdate(TokenProvider.hashToken(plainToken)));
    }

    // 만료·재사용(1회성 소진)·역할 불일치 링크를 차단 (이슈 #76 완료 조건 3가지)
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
        checkRoleConsistent(accessToken);
    }

    // 발급 이후 대체 담당자 전환(fallback)·차단(block) 등으로 이 단계가 더 이상 "발송됨(SENT)" 상태가
    // 아니게 되면, 같은 토큰이 가리키는 담당자·역할이 이메일을 받았을 때와 달라졌다는 뜻이라 차단한다.
    private void checkRoleConsistent(AccessToken accessToken) {
        if (accessToken.getHandoverStage().getStatus() != HandoverStageStatus.SENT) {
            publicLinkAuditor.recordStateFailure(ErrorCode.ACCESS_LINK_EXPIRED);
            throw new CustomException(ErrorCode.ACCESS_LINK_EXPIRED);
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
        int code = SECURE_RANDOM.nextInt(10_000);
        return String.format("%04d", code);
    }
}
