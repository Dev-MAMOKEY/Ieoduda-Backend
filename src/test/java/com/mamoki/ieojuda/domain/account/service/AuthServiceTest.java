package com.mamoki.ieojuda.domain.account.service;

import com.mamoki.ieojuda.domain.account.dto.LoginRequest;
import com.mamoki.ieojuda.domain.account.dto.RefreshRequest;
import com.mamoki.ieojuda.domain.account.dto.TokenResponse;
import com.mamoki.ieojuda.domain.account.entity.RefreshSession;
import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.RefreshSessionRepository;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.domain.audit.service.AuthAuditService;
import com.mamoki.ieojuda.global.jwt.component.JwtTokenProvider;
import com.mamoki.ieojuda.global.jwt.config.JwtProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// issue #56 회귀 테스트 - 로그인 시 세션 생성, refresh 시 회전(rotation), 이미 소모된 토큰 재사용 시
// family 전체 차단, 로그아웃 시 전체 세션 폐기 + tokenVersion 증가를 검증한다.
class AuthServiceTest {

    private UserRepository userRepository;
    private PlanRepository planRepository;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;
    private PasswordBreachChecker passwordBreachChecker;
    private LoginAttemptService loginAttemptService;
    private AuthAuditService authAuditService;
    private HttpServletRequest httpServletRequest;
    private JwtProperties jwtProperties;
    private RefreshSessionRepository refreshSessionRepository;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        planRepository = mock(PlanRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        passwordBreachChecker = mock(PasswordBreachChecker.class);
        loginAttemptService = mock(LoginAttemptService.class);
        authAuditService = mock(AuthAuditService.class);
        httpServletRequest = mock(HttpServletRequest.class);
        jwtProperties = mock(JwtProperties.class);
        refreshSessionRepository = mock(RefreshSessionRepository.class);
        // 실제 SessionRevocationService를 씀 - 재사용 탐지 시 family 전체가 진짜로 revoke()되는지까지
        // (mock 호출 검증이 아니라 엔티티 상태로) 확인하기 위함. 이걸로 REQUIRES_NEW 분리를 빼먹는
        // 회귀를 실제로 잡아냈다(#48/#55와 같은 롤백 함정이 refresh()에도 있었음).
        SessionRevocationService sessionRevocationService = new SessionRevocationService(refreshSessionRepository);

        when(jwtProperties.getRefreshTokenExpirationMs()).thenReturn(1_209_600_000L);

        authService = new AuthService(
                userRepository, planRepository, passwordEncoder, jwtTokenProvider,
                passwordBreachChecker, loginAttemptService, authAuditService, httpServletRequest,
                jwtProperties, refreshSessionRepository, sessionRevocationService);
    }

    private static final UUID USER_ID = UUID.randomUUID();

    private User userWithId(UUID id) {
        User user = User.builder().email("owner@test.com").password("hash").name("A").build();
        setField(user, "userId", id);
        return user;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void login_success_createsNewSessionFamily() {
        User user = userWithId(USER_ID);
        when(userRepository.findByEmail("owner@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password1234", "hash")).thenReturn(true);
        when(jwtTokenProvider.generateRefreshToken(eq(USER_ID), any(), anyLong())).thenReturn("rt-jwt");
        when(jwtTokenProvider.generateAccessToken(eq(USER_ID), eq("owner@test.com"), eq("USER"), eq(0))).thenReturn("at-jwt");

        TokenResponse response = authService.login(new LoginRequest("owner@test.com", "password1234"));

        assertThat(response.accessToken()).isEqualTo("at-jwt");
        assertThat(response.refreshToken()).isEqualTo("rt-jwt");

        ArgumentCaptor<RefreshSession> captor = ArgumentCaptor.forClass(RefreshSession.class);
        verify(refreshSessionRepository).save(captor.capture());
        RefreshSession saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getFamilyId()).isNotBlank();
        assertThat(saved.getTokenHash()).isEqualTo(TokenProvider.hashToken("rt-jwt"));
        assertThat(saved.getUsedAt()).isNull();
        assertThat(saved.getRevokedAt()).isNull();
    }

    @Test
    void refresh_validUnusedSession_rotatesAndLinksToNewSession() {
        User user = userWithId(USER_ID);
        RefreshSession oldSession = RefreshSession.builder()
                .sessionId("old-id")
                .user(user)
                .familyId("fam-1")
                .tokenHash(TokenProvider.hashToken("presented-rt"))
                .issuedAt(LocalDateTime.now().minusMinutes(5))
                .expiresAt(LocalDateTime.now().plusDays(14))
                .build();

        when(jwtTokenProvider.isRefreshToken("presented-rt")).thenReturn(true);
        when(jwtTokenProvider.getJti("presented-rt")).thenReturn("old-id");
        when(refreshSessionRepository.findById("old-id")).thenReturn(Optional.of(oldSession));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateRefreshToken(eq(USER_ID), any(), anyLong())).thenReturn("new-rt-jwt");
        when(jwtTokenProvider.generateAccessToken(eq(USER_ID), any(), any(), any())).thenReturn("new-at-jwt");

        TokenResponse response = authService.refresh(new RefreshRequest("presented-rt"));

        assertThat(response.refreshToken()).isEqualTo("new-rt-jwt");
        assertThat(oldSession.getUsedAt()).isNotNull();
        assertThat(oldSession.getReplacedBySessionId()).isNotBlank();

        ArgumentCaptor<RefreshSession> captor = ArgumentCaptor.forClass(RefreshSession.class);
        verify(refreshSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getFamilyId()).isEqualTo("fam-1");
        assertThat(captor.getValue().getSessionId()).isEqualTo(oldSession.getReplacedBySessionId());
    }

    @Test
    void refresh_alreadyUsedSession_isTreatedAsTheftAndRevokesWholeFamily() {
        User user = userWithId(USER_ID);
        RefreshSession reusedSession = RefreshSession.builder()
                .sessionId("stolen-id")
                .user(user)
                .familyId("fam-2")
                .tokenHash(TokenProvider.hashToken("stolen-rt"))
                .issuedAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(13))
                .build();
        reusedSession.markUsed("some-later-session"); // 이미 한 번 회전되어 소모된 상태

        RefreshSession siblingInFamily = RefreshSession.builder()
                .sessionId("some-later-session")
                .user(user)
                .familyId("fam-2")
                .tokenHash("irrelevant")
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(14))
                .build();

        when(jwtTokenProvider.isRefreshToken("stolen-rt")).thenReturn(true);
        when(jwtTokenProvider.getJti("stolen-rt")).thenReturn("stolen-id");
        when(refreshSessionRepository.findById("stolen-id")).thenReturn(Optional.of(reusedSession));
        when(refreshSessionRepository.findByFamilyIdAndRevokedAtIsNull("fam-2"))
                .thenReturn(List.of(reusedSession, siblingInFamily));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("stolen-rt")))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_REUSE_DETECTED));

        assertThat(reusedSession.getRevokedAt()).isNotNull();
        assertThat(siblingInFamily.getRevokedAt()).isNotNull();
        verify(refreshSessionRepository, never()).save(any());
    }

    @Test
    void refresh_revokedSession_isRejected() {
        User user = userWithId(USER_ID);
        RefreshSession revokedSession = RefreshSession.builder()
                .sessionId("revoked-id")
                .user(user)
                .familyId("fam-3")
                .tokenHash(TokenProvider.hashToken("revoked-rt"))
                .issuedAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(13))
                .build();
        revokedSession.revoke();

        when(jwtTokenProvider.isRefreshToken("revoked-rt")).thenReturn(true);
        when(jwtTokenProvider.getJti("revoked-rt")).thenReturn("revoked-id");
        when(refreshSessionRepository.findById("revoked-id")).thenReturn(Optional.of(revokedSession));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("revoked-rt")))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_REVOKED));
    }

    @Test
    void logout_revokesAllActiveSessionsAndBumpsTokenVersion() {
        User user = userWithId(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        RefreshSession sessionA = RefreshSession.builder()
                .sessionId("a").user(user).familyId("fam-a").tokenHash("h")
                .issuedAt(LocalDateTime.now()).expiresAt(LocalDateTime.now().plusDays(14)).build();
        RefreshSession sessionB = RefreshSession.builder()
                .sessionId("b").user(user).familyId("fam-b").tokenHash("h")
                .issuedAt(LocalDateTime.now()).expiresAt(LocalDateTime.now().plusDays(14)).build();

        when(refreshSessionRepository.findByUser_UserIdAndRevokedAtIsNull(USER_ID)).thenReturn(List.of(sessionA, sessionB));

        int versionBefore = user.getTokenVersion();
        authService.logout(USER_ID);

        assertThat(sessionA.getRevokedAt()).isNotNull();
        assertThat(sessionB.getRevokedAt()).isNotNull();
        assertThat(user.getTokenVersion()).isEqualTo(versionBefore + 1);
    }
}
