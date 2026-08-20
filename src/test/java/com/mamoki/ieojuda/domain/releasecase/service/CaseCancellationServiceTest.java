package com.mamoki.ieojuda.domain.releasecase.service;

import com.mamoki.ieojuda.domain.releasecase.dto.ReleaseStatusResponse;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCaseStatus;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityToken;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 완료 조건 - "만료·사용된 경고 링크는 재사용할 수 없다"는 SecurityTokenService.resolve()/consume()이
// 이미 검증하므로(만료·사용·폐기·목적 불일치), 여기서는 CANCEL_CASE 토큰이 그 검증을 실제로 거치는지와
// URL의 caseId가 토큰이 가리키는 사건과 다를 때 거절되는지를 확인한다 (ObjectionServiceTest와 동일 패턴).
class CaseCancellationServiceTest {

    private static final UUID CASE_ID = UUID.randomUUID();
    private static final UUID OTHER_CASE_ID = UUID.randomUUID();

    private SecurityTokenService securityTokenService;
    private CaseCancellationService caseCancellationService;

    private ReleaseCase releaseCase;

    @BeforeEach
    void setUp() {
        securityTokenService = mock(SecurityTokenService.class);
        caseCancellationService = new CaseCancellationService(securityTokenService);

        releaseCase = mock(ReleaseCase.class);
        when(releaseCase.getCaseId()).thenReturn(CASE_ID);
    }

    private SecurityToken tokenFor(ReleaseCase releaseCase) {
        SecurityToken token = mock(SecurityToken.class);
        when(token.getReleaseCase()).thenReturn(releaseCase);
        return token;
    }

    @Test
    void cancel_whenTokenBoundToDifferentCase_throwsForbiddenWithoutCanceling() {
        SecurityToken token = tokenFor(releaseCase);
        when(securityTokenService.resolve(eq("token"), eq(SecurityTokenPurpose.CANCEL_CASE))).thenReturn(token);

        assertThatThrownBy(() -> caseCancellationService.cancel(OTHER_CASE_ID, "token"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(releaseCase, never()).cancel();
        verify(securityTokenService, never()).consume(token);
    }

    @Test
    void cancel_whenTokenMatchesCase_cancelsAndConsumesTokenAndRevokesRemainingTokens() {
        SecurityToken token = tokenFor(releaseCase);
        when(securityTokenService.resolve(eq("token"), eq(SecurityTokenPurpose.CANCEL_CASE))).thenReturn(token);
        when(releaseCase.getStatus()).thenReturn(ReleaseCaseStatus.CANCELED);

        ReleaseStatusResponse response = caseCancellationService.cancel(CASE_ID, "token");

        assertThat(response).isNotNull();
        verify(releaseCase).cancel();
        verify(securityTokenService).consume(token);
        verify(securityTokenService).revokeAllForCase(releaseCase, SecurityTokenPurpose.UPLOAD_EVIDENCE);
        verify(securityTokenService).revokeAllForCase(releaseCase, SecurityTokenPurpose.RAISE_OBJECTION);
    }

    // 만료·사용된 토큰은 SecurityTokenService.resolve()가 먼저 예외를 던지므로 취소가 실행되지 않는다
    @Test
    void cancel_whenTokenExpired_propagatesExceptionWithoutCanceling() {
        when(securityTokenService.resolve(eq("token"), eq(SecurityTokenPurpose.CANCEL_CASE)))
                .thenThrow(new CustomException(ErrorCode.ACCESS_LINK_EXPIRED));

        assertThatThrownBy(() -> caseCancellationService.cancel(CASE_ID, "token"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_LINK_EXPIRED));
        verify(releaseCase, never()).cancel();
    }

    @Test
    void cancel_whenTokenAlreadyUsed_propagatesExceptionWithoutCanceling() {
        when(securityTokenService.resolve(eq("token"), eq(SecurityTokenPurpose.CANCEL_CASE)))
                .thenThrow(new CustomException(ErrorCode.ACCESS_LINK_ALREADY_USED));

        assertThatThrownBy(() -> caseCancellationService.cancel(CASE_ID, "token"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_LINK_ALREADY_USED));
        verify(releaseCase, never()).cancel();
    }
}
