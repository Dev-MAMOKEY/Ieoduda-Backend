package com.mamoki.ieojuda.domain.securitytoken.service;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityToken;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.repository.SecurityTokenRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.ratelimit.TokenLookupGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// issue #41 완료 조건 - "만료·사용·폐기·목적 불일치 토큰이 모두 거절된다"를 SecurityTokenService.resolve()
// 단위로 직접 검증한다. 실제 도메인 서비스(ConfirmerInviteService 등)는 이 메서드를 거칠 뿐이므로,
// 여기서 4가지 거절 사유를 각각 검증해두면 모든 목적(ACCEPT_ROLE/REPORT_DEATH/UPLOAD_EVIDENCE/RAISE_OBJECTION)에
// 공통으로 적용된다.
class SecurityTokenServiceTest {

    private SecurityTokenRepository securityTokenRepository;
    private TokenLookupGuard tokenLookupGuard;
    private SecurityTokenService securityTokenService;

    @BeforeEach
    void setUp() {
        securityTokenRepository = mock(SecurityTokenRepository.class);
        tokenLookupGuard = mock(TokenLookupGuard.class);
        securityTokenService = new SecurityTokenService(securityTokenRepository, tokenLookupGuard);

        // 실제 구현처럼 supplier를 그대로 실행해 securityTokenRepository 목 설정이 동작하도록 위임한다.
        when(tokenLookupGuard.resolve(anyString(), any())).thenAnswer(invocation -> {
            Supplier<Optional<?>> lookup = invocation.getArgument(1);
            return lookup.get().orElseThrow(() -> new CustomException(ErrorCode.TOKEN_INVALID));
        });
    }

    private SecurityToken buildToken(SecurityTokenPurpose purpose, LocalDateTime expiresAt) {
        return SecurityToken.builder()
                .tokenHash("hash").purpose(purpose).confirmer(mock(Confirmer.class)).expiresAt(expiresAt).build();
    }

    @Test
    void resolve_whenPurposeMismatches_throwsTokenPurposeMismatch() {
        SecurityToken token = buildToken(SecurityTokenPurpose.ACCEPT_ROLE, LocalDateTime.now().plusHours(1));
        when(securityTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        // 수락 토큰으로 사망 신고를 시도 - 완료 조건 "수락 토큰으로 사망 신고·증빙 업로드·이의 제기를 수행할 수 없다"
        assertThatThrownBy(() -> securityTokenService.resolve("plain", SecurityTokenPurpose.REPORT_DEATH))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOKEN_PURPOSE_MISMATCH));
    }

    @Test
    void resolve_whenRevoked_throwsTokenRevoked() {
        SecurityToken token = buildToken(SecurityTokenPurpose.REPORT_DEATH, LocalDateTime.now().plusHours(1));
        token.revoke();
        when(securityTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> securityTokenService.resolve("plain", SecurityTokenPurpose.REPORT_DEATH))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOKEN_REVOKED));
    }

    @Test
    void resolve_whenAlreadyUsed_throwsAccessLinkAlreadyUsed() {
        SecurityToken token = buildToken(SecurityTokenPurpose.ACCEPT_ROLE, LocalDateTime.now().plusHours(1));
        ReflectionTestUtils.setField(token, "usedAt", LocalDateTime.now().minusMinutes(1));
        when(securityTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> securityTokenService.resolve("plain", SecurityTokenPurpose.ACCEPT_ROLE))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_LINK_ALREADY_USED));
    }

    @Test
    void resolve_whenExpired_throwsAccessLinkExpired() {
        SecurityToken token = buildToken(SecurityTokenPurpose.UPLOAD_EVIDENCE, LocalDateTime.now().minusMinutes(1));
        when(securityTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> securityTokenService.resolve("plain", SecurityTokenPurpose.UPLOAD_EVIDENCE))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_LINK_EXPIRED));
    }

    @Test
    void consume_whenMarkUsedAffectsZeroRows_throwsAccessLinkAlreadyUsed() {
        SecurityToken token = buildToken(SecurityTokenPurpose.RAISE_OBJECTION, LocalDateTime.now().plusHours(1));
        // 동시 요청 경쟁에서 진 쪽 - 원자적 UPDATE가 영향받은 행 0을 반환
        when(securityTokenRepository.markUsedIfUnused(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> securityTokenService.consume(token))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_LINK_ALREADY_USED));
    }

    @Test
    void consume_whenMarkUsedAffectsOneRow_succeeds() {
        SecurityToken token = buildToken(SecurityTokenPurpose.RAISE_OBJECTION, LocalDateTime.now().plusHours(1));
        when(securityTokenRepository.markUsedIfUnused(any(), any())).thenReturn(1);

        securityTokenService.consume(token);
        // 예외 없이 끝나면 성공
    }
}
