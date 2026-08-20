package com.mamoki.ieojuda.domain.postaccess.service;

import com.mamoki.ieojuda.domain.account.entity.User;
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
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxService;
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
    private EmailOutboxService emailOutboxService;
    private AppProperties appProperties;
    private TokenLookupGuard tokenLookupGuard;
    private PublicLinkAuditor publicLinkAuditor;
    private OtpAttemptRecorder otpAttemptRecorder;
    private PosthumousAccessService posthumousAccessService;

    private static final String PLAIN_TOKEN = "plain-access-token";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        accessTokenRepository = mock(AccessTokenRepository.class);
        emailOutboxService = mock(EmailOutboxService.class);
        appProperties = mock(AppProperties.class);
        tokenLookupGuard = mock(TokenLookupGuard.class);
        publicLinkAuditor = mock(PublicLinkAuditor.class);
        // 실제 구현처럼 별도 REQUIRES_NEW 트랜잭션에서 시도 횟수를 증가시키는 동작을 재현하기 위해
        // 목이 아닌 실제 인스턴스를 쓴다(트랜잭션 자체는 이 단위 테스트 범위 밖 - HTTP 통합 테스트가 검증).
        otpAttemptRecorder = new OtpAttemptRecorder(accessTokenRepository);
        posthumousAccessService = new PosthumousAccessService(
                accessTokenRepository, emailOutboxService, appProperties,
                tokenLookupGuard, publicLinkAuditor, otpAttemptRecorder);

        when(appProperties.getContactEmail()).thenReturn("support@ieoduda.example");
        when(appProperties.getInviteTokenTtlHours()).thenReturn(72L);

        // TokenLookupGuard는 실제 구현처럼 supplier를 그대로 실행해 accessTokenRepository 목 설정에 위임한다.
        when(tokenLookupGuard.resolve(anyString(), any())).thenAnswer(invocation -> {
            Supplier<Optional<?>> lookup = invocation.getArgument(1);
            return lookup.get().orElseThrow(() -> new CustomException(ErrorCode.TOKEN_INVALID));
        });
    }

    // 기본값: 단계가 실제로 발송된(SENT) 상태 - 링크가 정상적으로 유효한 경우
    private AccessToken buildAccessToken(LocalDateTime expiresAt) {
        return buildAccessToken(expiresAt, HandoverStage::send);
    }

    private AccessToken buildAccessToken(LocalDateTime expiresAt, java.util.function.Consumer<HandoverStage> stageState) {
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
        stageState.accept(stage);

        AccessToken accessToken = AccessToken.builder()
                .handoverStage(stage)
                .tokenHash(TokenProvider.hashToken(PLAIN_TOKEN))
                .expiresAt(expiresAt)
                .build();
        when(accessTokenRepository.findByTokenHash(TokenProvider.hashToken(PLAIN_TOKEN)))
                .thenReturn(Optional.of(accessToken));
        // sendOtp()는 쿨다운 경쟁 조건을 막기 위해 잠금 조회를 쓴다 - 단위 테스트에선 실제 잠금 대신
        // 동일한 값을 반환하는 목으로 대체(잠금 자체의 동시성 효과는 HTTP 통합 테스트가 검증)
        when(accessTokenRepository.findByTokenHashForUpdate(TokenProvider.hashToken(PLAIN_TOKEN)))
                .thenReturn(Optional.of(accessToken));
        // OtpAttemptRecorder(REQUIRES_NEW)가 tokenId로 다시 조회하는 부분을 재현
        when(accessTokenRepository.findById(any())).thenReturn(Optional.of(accessToken));
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
        accessToken.verify();

        assertThatThrownBy(() -> posthumousAccessService.getAccess(PLAIN_TOKEN))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_LINK_ALREADY_USED));
    }

    // issue #76 완료 조건 - "역할 불일치" 차단: 링크 발급 이후 대체 담당자 전환(fallback) 등으로 이
    // 단계가 더 이상 SENT 상태가 아니게 되면, 아직 만료되지 않은 링크라도 차단해야 한다.
    @Test
    void getAccess_whenStageNoLongerSent_throwsAccessLinkExpired() {
        buildAccessToken(LocalDateTime.now().plusHours(1), stage -> {
            stage.send();
            stage.block(); // 대체 담당자 없이 fallback 시도 -> 단계 차단
        });

        assertThatThrownBy(() -> posthumousAccessService.getAccess(PLAIN_TOKEN))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_LINK_EXPIRED));
    }

    @Test
    void sendOtp_whenStageNoLongerSent_throwsAccessLinkExpired() {
        buildAccessToken(LocalDateTime.now().plusHours(1), stage -> {}); // send() 호출 안 함 -> 아직 PENDING

        assertThatThrownBy(() -> posthumousAccessService.sendOtp(PLAIN_TOKEN))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_LINK_EXPIRED));
    }

    @Test
    void verify_whenCodeCorrect_issuesSessionAndConsumesLink() {
        AccessToken accessToken = buildAccessToken(LocalDateTime.now().plusHours(1));
        accessToken.recordOtpSend(TokenProvider.hashToken("123456"));

        var response = posthumousAccessService.verify(PLAIN_TOKEN, new OtpVerifyRequest("123456"));

        // 새 컬럼 없이 기존 필드만 재사용하는 설계 - 같은 원본 토큰이 그대로 열람 세션 식별자가 된다
        assertThat(response.accessSessionId()).isEqualTo(PLAIN_TOKEN);
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
