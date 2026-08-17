package com.mamoki.ieojuda.domain.postaccess.service;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.audit.repository.EmailLogRepository;
import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.postaccess.dto.OtpVerifyRequest;
import com.mamoki.ieojuda.domain.postaccess.dto.PosthumousAccessResponse;
import com.mamoki.ieojuda.domain.postaccess.entity.AccessToken;
import com.mamoki.ieojuda.domain.postaccess.repository.AccessTokenRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.entity.RoleType;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailSendResult;
import com.mamoki.ieojuda.global.email.sender.EmailSender;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.ratelimit.PublicLinkAuditor;
import com.mamoki.ieojuda.global.ratelimit.TokenLookupGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// issue #76 - 사후 인계 링크 검증 + OTP 2단계 확인. 만료·재사용 링크 차단과 OTP 반복 실패 차단을 검증한다.
class PosthumousAccessServiceTest {

    private AccessTokenRepository accessTokenRepository;
    private EmailLogRepository emailLogRepository;
    private EmailSender emailSender;
    private AppProperties appProperties;
    private TokenLookupGuard tokenLookupGuard;
    private PublicLinkAuditor publicLinkAuditor;
    private PosthumousAccessService posthumousAccessService;

    private static final String PLAIN_TOKEN = "plain-access-token";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        accessTokenRepository = mock(AccessTokenRepository.class);
        emailLogRepository = mock(EmailLogRepository.class);
        emailSender = mock(EmailSender.class);
        appProperties = mock(AppProperties.class);
        tokenLookupGuard = mock(TokenLookupGuard.class);
        publicLinkAuditor = mock(PublicLinkAuditor.class);
        posthumousAccessService = new PosthumousAccessService(
                accessTokenRepository, emailLogRepository, emailSender, appProperties,
                tokenLookupGuard, publicLinkAuditor);

        when(appProperties.getContactEmail()).thenReturn("support@ieoduda.example");
        when(appProperties.getInviteTokenTtlHours()).thenReturn(72L);

        // TokenLookupGuard는 실제 구현처럼 supplier를 그대로 실행해 accessTokenRepository 목 설정에 위임한다.
        when(tokenLookupGuard.resolve(anyString(), any())).thenAnswer(invocation -> {
            Supplier<Optional<?>> lookup = invocation.getArgument(1);
            return lookup.get().orElseThrow(() -> new CustomException(ErrorCode.TOKEN_INVALID));
        });

        when(emailSender.send(anyString(), any())).thenReturn(EmailSendResult.success("msg-1"));
        when(emailLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private AccessToken buildAccessToken(LocalDateTime expiresAt) {
        User user = mock(User.class);
        when(user.getName()).thenReturn("김나무");
        Plan plan = mock(Plan.class);
        when(plan.getUser()).thenReturn(user);
        LifeArea lifeArea = mock(LifeArea.class);

        Recipient recipient = Recipient.builder()
                .plan(plan)
                .lifeArea(lifeArea)
                .name("이지수")
                .email("jisoo@naver.com")
                .roleType(RoleType.RELATIONSHIP_MANAGER)
                .isBackup(false)
                .disclosureScope(DisclosureScope.RELATIONSHIP)
                .maxWaitHours(168)
                .backupFor(null)
                .build();

        HandoverStage stage = HandoverStage.builder()
                .plan(plan)
                .recipient(recipient)
                .stageOrder(0)
                .build();

        AccessToken accessToken = AccessToken.builder()
                .handoverStage(stage)
                .tokenHash(TokenProvider.hashToken(PLAIN_TOKEN))
                .expiresAt(expiresAt)
                .build();
        when(accessTokenRepository.findByTokenHash(TokenProvider.hashToken(PLAIN_TOKEN)))
                .thenReturn(Optional.of(accessToken));
        return accessToken;
    }

    @Test
    void getAccess_whenExpired_throwsAccessLinkExpired() {
        buildAccessToken(LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> posthumousAccessService.getAccess(PLAIN_TOKEN))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_LINK_EXPIRED));
    }

    @Test
    void getAccess_whenValid_returnsWithoutHandoverContent() {
        buildAccessToken(LocalDateTime.now().plusHours(1));

        PosthumousAccessResponse response = posthumousAccessService.getAccess(PLAIN_TOKEN);

        assertThat(response.recipientName()).isEqualTo("이지수");
        assertThat(response.authorName()).isEqualTo("김나무");
        assertThat(response.roleLabel()).isEqualTo("관계 정리 담당자");
        assertThat(response.otpSent()).isFalse();
    }

    @Test
    void getAccess_whenAlreadyUsed_throwsAccessLinkAlreadyUsed() {
        AccessToken accessToken = buildAccessToken(LocalDateTime.now().plusHours(1));
        accessToken.verify("session-hash", LocalDateTime.now().plusMinutes(60));

        assertThatThrownBy(() -> posthumousAccessService.getAccess(PLAIN_TOKEN))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_LINK_ALREADY_USED));
    }

    @Test
    void verify_whenCodeCorrect_issuesSessionAndConsumesLink() {
        AccessToken accessToken = buildAccessToken(LocalDateTime.now().plusHours(1));
        accessToken.recordOtpSend(TokenProvider.hashToken("123456"));

        var response = posthumousAccessService.verify(PLAIN_TOKEN, new OtpVerifyRequest("123456"));

        assertThat(response.accessSessionId()).isNotBlank();
        assertThat(response.sessionExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(accessToken.getUsed()).isTrue();
        assertThat(accessToken.getVerifiedAt()).isNotNull();
    }

    @Test
    void verify_whenAttemptsExceedLimit_locksTokenEvenWithCorrectCode() {
        AccessToken accessToken = buildAccessToken(LocalDateTime.now().plusHours(1));
        accessToken.recordOtpSend(TokenProvider.hashToken("123456"));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> posthumousAccessService.verify(PLAIN_TOKEN, new OtpVerifyRequest("000000")))
                    .isInstanceOfSatisfying(CustomException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OTP_VERIFICATION_FAILED));
        }
        assertThat(accessToken.getAttemptCount()).isEqualTo(5);

        // 5회를 넘긴 뒤에는 정답 코드를 넣어도 차단된다
        assertThatThrownBy(() -> posthumousAccessService.verify(PLAIN_TOKEN, new OtpVerifyRequest("123456")))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOKEN_TEMPORARILY_LOCKED));
    }

    @Test
    void sendOtp_returnsMaskedEmailAndExpiry() {
        buildAccessToken(LocalDateTime.now().plusHours(1));

        var response = posthumousAccessService.sendOtp(PLAIN_TOKEN);

        assertThat(response.maskedEmail()).isEqualTo("ji***@naver.com");
        assertThat(response.otpExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(response.resendAvailableAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void sendOtp_withinCooldown_throwsTooManyRequests() {
        AccessToken accessToken = buildAccessToken(LocalDateTime.now().plusHours(1));
        accessToken.recordOtpSend(TokenProvider.hashToken("123456")); // 방금 발송된 상태

        assertThatThrownBy(() -> posthumousAccessService.sendOtp(PLAIN_TOKEN))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS));
    }
}
