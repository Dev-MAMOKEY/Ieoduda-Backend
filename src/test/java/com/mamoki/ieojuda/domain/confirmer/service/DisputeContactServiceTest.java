package com.mamoki.ieojuda.domain.confirmer.service;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.confirmer.entity.DisputeContact;
import com.mamoki.ieojuda.domain.confirmer.repository.DisputeContactRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.service.PlanOwnershipReader;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxService;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.ratelimit.PublicLinkAuditor;
import com.mamoki.ieojuda.global.ratelimit.TokenLookupGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// issue #92 완료 조건 - "검증 메일을 다시 보낼 수 있다" / "재발송 시 이전 링크는 무효화된다"의
// Recipient/Confirmer 재발송과 동일한 패턴(플랫 경로 + plan.user로 소유권 확인, 이미 검증 완료 시 409)
class DisputeContactServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID CONTACT_ID = UUID.randomUUID();

    private PlanOwnershipReader planOwnershipReader;
    private DisputeContactRepository disputeContactRepository;
    private EmailOutboxService emailOutboxService;
    private AppProperties appProperties;
    private TokenLookupGuard tokenLookupGuard;
    private PublicLinkAuditor publicLinkAuditor;
    private SecurityTokenService securityTokenService;
    private DisputeContactService disputeContactService;

    private Plan plan;

    @BeforeEach
    void setUp() {
        planOwnershipReader = mock(PlanOwnershipReader.class);
        disputeContactRepository = mock(DisputeContactRepository.class);
        emailOutboxService = mock(EmailOutboxService.class);
        appProperties = mock(AppProperties.class);
        tokenLookupGuard = mock(TokenLookupGuard.class);
        publicLinkAuditor = mock(PublicLinkAuditor.class);
        securityTokenService = mock(SecurityTokenService.class);
        disputeContactService = new DisputeContactService(planOwnershipReader, disputeContactRepository,
                emailOutboxService, appProperties, tokenLookupGuard, publicLinkAuditor, securityTokenService);

        when(appProperties.getInviteTokenTtlHours()).thenReturn(48L);
        when(appProperties.getBaseUrl()).thenReturn("https://ieoduda.example.com");
        when(appProperties.getContactEmail()).thenReturn("support@ieoduda.example.com");

        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(USER_ID);
        plan = mock(Plan.class);
        when(plan.getUser()).thenReturn(owner);
    }

    private DisputeContact buildContact(boolean verified) {
        DisputeContact contact = DisputeContact.builder().plan(plan).name("이지수").email("jisu@test.com").build();
        if (verified) {
            contact.verify();
        }
        when(disputeContactRepository.findById(CONTACT_ID)).thenReturn(Optional.of(contact));
        return contact;
    }

    @Test
    void resendVerificationEmail_whenNotYetVerified_issuesNewTokenAndSendsEmail() {
        DisputeContact contact = buildContact(false);
        String originalToken = contact.getInviteToken();

        var response = disputeContactService.resendVerificationEmail(USER_ID, CONTACT_ID);

        assertThat(response.contactId()).isEqualTo(contact.getContactId());
        assertThat(response.emailSent()).isTrue();
        // issueInviteToken()이 새 해시로 덮어써서 이전 토큰은 더 이상 조회되지 않는다(자동 무효화)
        assertThat(contact.getInviteToken()).isNotEqualTo(originalToken);
        org.mockito.Mockito.verify(emailOutboxService).enqueue(any(), any(), any(), any(), any());
    }

    @Test
    void resendVerificationEmail_whenAlreadyVerified_throwsDisputeContactResendNotAllowed() {
        buildContact(true);

        assertThatThrownBy(() -> disputeContactService.resendVerificationEmail(USER_ID, CONTACT_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DISPUTE_CONTACT_RESEND_NOT_ALLOWED));
    }

    @Test
    void resendVerificationEmail_whenNotOwner_throwsDisputeContactNotFound() {
        buildContact(false);

        assertThatThrownBy(() -> disputeContactService.resendVerificationEmail(OTHER_USER_ID, CONTACT_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DISPUTE_CONTACT_NOT_FOUND));
    }

    @Test
    void resendVerificationEmail_whenContactDoesNotExist_throwsDisputeContactNotFound() {
        when(disputeContactRepository.findById(CONTACT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> disputeContactService.resendVerificationEmail(USER_ID, CONTACT_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DISPUTE_CONTACT_NOT_FOUND));
    }
}
