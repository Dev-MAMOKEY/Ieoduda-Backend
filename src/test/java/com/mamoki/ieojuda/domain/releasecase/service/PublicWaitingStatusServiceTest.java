package com.mamoki.ieojuda.domain.releasecase.service;

import com.mamoki.ieojuda.domain.releasecase.dto.PublicWaitingStatusResponse;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCaseStatus;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityToken;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// waiting 링크는 작성자 본인(CANCEL_CASE)과 이의 제기 연락처(RAISE_OBJECTION)에게 같은 URL 형태로
// 발송되므로, 조회는 두 목적의 토큰을 모두 받아들이되 토큰의 목적에 따라 availableAction이 갈려야 한다.
class PublicWaitingStatusServiceTest {

    private static final UUID CASE_ID = UUID.randomUUID();
    private static final UUID OTHER_CASE_ID = UUID.randomUUID();

    private SecurityTokenService securityTokenService;
    private PublicWaitingStatusService publicWaitingStatusService;
    private ReleaseCase releaseCase;

    @BeforeEach
    void setUp() {
        securityTokenService = mock(SecurityTokenService.class);
        publicWaitingStatusService = new PublicWaitingStatusService(securityTokenService);

        releaseCase = mock(ReleaseCase.class);
        when(releaseCase.getCaseId()).thenReturn(CASE_ID);
        when(releaseCase.getStatus()).thenReturn(ReleaseCaseStatus.WAITING);
    }

    private SecurityToken tokenFor(SecurityTokenPurpose purpose, ReleaseCase releaseCase) {
        SecurityToken token = mock(SecurityToken.class);
        when(token.getPurpose()).thenReturn(purpose);
        when(token.getReleaseCase()).thenReturn(releaseCase);
        return token;
    }

    @Test
    void getStatus_withCancelCaseToken_returnsCancelAction() {
        SecurityToken token = tokenFor(SecurityTokenPurpose.CANCEL_CASE, releaseCase);
        when(securityTokenService.resolveAny(eq("token"),
                eq(EnumSet.of(SecurityTokenPurpose.CANCEL_CASE, SecurityTokenPurpose.RAISE_OBJECTION))))
                .thenReturn(token);

        PublicWaitingStatusResponse response = publicWaitingStatusService.getStatus(CASE_ID, "token");

        assertThat(response.availableAction()).isEqualTo("CANCEL");
        assertThat(response.hasActiveCase()).isTrue();
        assertThat(response.status()).isEqualTo("WAITING");
    }

    @Test
    void getStatus_withRaiseObjectionToken_returnsRaiseObjectionAction() {
        SecurityToken token = tokenFor(SecurityTokenPurpose.RAISE_OBJECTION, releaseCase);
        when(securityTokenService.resolveAny(eq("token"),
                eq(EnumSet.of(SecurityTokenPurpose.CANCEL_CASE, SecurityTokenPurpose.RAISE_OBJECTION))))
                .thenReturn(token);

        PublicWaitingStatusResponse response = publicWaitingStatusService.getStatus(CASE_ID, "token");

        assertThat(response.availableAction()).isEqualTo("RAISE_OBJECTION");
    }

    @Test
    void getStatus_whenTokenBoundToDifferentCase_throwsForbidden() {
        SecurityToken token = tokenFor(SecurityTokenPurpose.CANCEL_CASE, releaseCase);
        when(securityTokenService.resolveAny(eq("token"),
                eq(EnumSet.of(SecurityTokenPurpose.CANCEL_CASE, SecurityTokenPurpose.RAISE_OBJECTION))))
                .thenReturn(token);

        assertThatThrownBy(() -> publicWaitingStatusService.getStatus(OTHER_CASE_ID, "token"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }
}
