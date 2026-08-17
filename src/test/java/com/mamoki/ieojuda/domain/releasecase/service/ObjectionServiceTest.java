package com.mamoki.ieojuda.domain.releasecase.service;

import com.mamoki.ieojuda.domain.confirmer.entity.DisputeContact;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.releasecase.dto.ObjectionRequest;
import com.mamoki.ieojuda.domain.releasecase.entity.Objection;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.repository.ObjectionRepository;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityToken;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.ratelimit.PublicLinkAuditor;
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

// issue #41 - 이의 제기는 검증된 이의 제기 연락처의 RAISE_OBJECTION 전용 토큰(사건 바인딩)으로만
// 접수할 수 있어야 하고, 토큰이 가리키는 사건과 URL의 caseId가 다르면 거절되어야 한다.
class ObjectionServiceTest {

    private static final UUID CASE_ID = UUID.randomUUID();
    private static final UUID OTHER_CASE_ID = UUID.randomUUID();

    private ObjectionRepository objectionRepository;
    private PublicLinkAuditor publicLinkAuditor;
    private SecurityTokenService securityTokenService;
    private ObjectionService objectionService;

    private DisputeContact contact;
    private ReleaseCase releaseCase;
    private Plan plan;

    @BeforeEach
    void setUp() {
        objectionRepository = mock(ObjectionRepository.class);
        publicLinkAuditor = mock(PublicLinkAuditor.class);
        securityTokenService = mock(SecurityTokenService.class);
        objectionService = new ObjectionService(objectionRepository, publicLinkAuditor, securityTokenService);

        plan = mock(Plan.class);
        contact = mock(DisputeContact.class);
        when(contact.getPlan()).thenReturn(plan);
        releaseCase = mock(ReleaseCase.class);
        when(releaseCase.getCaseId()).thenReturn(CASE_ID);
        when(objectionRepository.save(org.mockito.ArgumentMatchers.any(Objection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private SecurityToken tokenFor(DisputeContact contact, ReleaseCase releaseCase) {
        SecurityToken token = mock(SecurityToken.class);
        when(token.getDisputeContact()).thenReturn(contact);
        when(token.getReleaseCase()).thenReturn(releaseCase);
        return token;
    }

    @Test
    void raise_whenContactNotVerified_throwsDisputeContactNotVerified() {
        when(contact.getIsVerified()).thenReturn(false);
        SecurityToken token = tokenFor(contact, releaseCase);
        when(securityTokenService.resolve(eq("token"), eq(SecurityTokenPurpose.RAISE_OBJECTION))).thenReturn(token);

        assertThatThrownBy(() -> objectionService.raise(CASE_ID, new ObjectionRequest("token", "사유")))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DISPUTE_CONTACT_NOT_VERIFIED));
        verify(objectionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void raise_whenTokenBoundToDifferentCase_throwsForbidden() {
        when(contact.getIsVerified()).thenReturn(true);
        SecurityToken token = tokenFor(contact, releaseCase);
        when(securityTokenService.resolve(eq("token"), eq(SecurityTokenPurpose.RAISE_OBJECTION))).thenReturn(token);

        // URL의 caseId가 토큰이 가리키는 사건과 다르다 - 다른 사건에 끼워 넣는 시도
        assertThatThrownBy(() -> objectionService.raise(OTHER_CASE_ID, new ObjectionRequest("token", "사유")))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(objectionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void raise_whenVerifiedAndCaseMatches_savesObjectionAndConsumesToken() {
        when(contact.getIsVerified()).thenReturn(true);
        SecurityToken token = tokenFor(contact, releaseCase);
        when(securityTokenService.resolve(eq("token"), eq(SecurityTokenPurpose.RAISE_OBJECTION))).thenReturn(token);

        objectionService.raise(CASE_ID, new ObjectionRequest("token", "본인이 살아있습니다"));

        verify(objectionRepository).save(org.mockito.ArgumentMatchers.any(Objection.class));
        verify(releaseCase).raiseDispute();
        verify(securityTokenService).consume(token);
    }
}
