package com.mamoki.ieojuda.global.ratelimit;

import com.mamoki.ieojuda.domain.audit.entity.AuthAuditEventType;
import com.mamoki.ieojuda.domain.audit.service.AuthAuditService;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// issue #55 회귀 테스트 - TokenLookupGuard가 존재하지 않는 토큰을 실패로 기록하고,
// 임계치를 넘으면 조회 자체를 시도하지 않고 바로 막는지 검증한다.
class TokenLookupGuardTest {

    private TokenAttemptService tokenAttemptService;
    private AuthAuditService authAuditService;
    private TokenLookupGuard guard;

    @BeforeEach
    void setUp() {
        tokenAttemptService = mock(TokenAttemptService.class);
        authAuditService = mock(AuthAuditService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("1.2.3.4");
        guard = new TokenLookupGuard(tokenAttemptService, authAuditService, request);
    }

    @Test
    void validToken_returnsValueAndRecordsSuccess() {
        when(tokenAttemptService.isLocked("valid-token")).thenReturn(false);

        String result = guard.resolve("valid-token", () -> Optional.of("found-entity"));

        assertThat(result).isEqualTo("found-entity");
        verify(tokenAttemptService).recordSuccess("valid-token");
    }

    @Test
    void unknownToken_recordsFailureAndThrowsTokenInvalid() {
        when(tokenAttemptService.isLocked("bad-token")).thenReturn(false);
        when(tokenAttemptService.recordFailure("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> guard.resolve("bad-token", Optional::empty))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID));

        verify(authAuditService).record(null, "1.2.3.4", AuthAuditEventType.PUBLIC_LINK_TOKEN_FAILURE, null);
    }

    @Test
    void alreadyLockedToken_blocksWithoutEvenAttemptingLookup() {
        when(tokenAttemptService.isLocked("locked-token")).thenReturn(true);

        assertThatThrownBy(() -> guard.resolve("locked-token", () -> {
            throw new AssertionError("잠긴 토큰인데 실제 조회를 시도해버렸다");
        })).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOKEN_TEMPORARILY_LOCKED));

        verify(authAuditService).record(null, "1.2.3.4", AuthAuditEventType.PUBLIC_LINK_TOKEN_LOCKED_ATTEMPT, null);
    }
}
