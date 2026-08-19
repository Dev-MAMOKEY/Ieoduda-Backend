package com.mamoki.ieojuda.domain.confirmer.service;

import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerDecisionRequest;
import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerDecisionResponse;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.entity.Relationship;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityToken;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxService;
import com.mamoki.ieojuda.global.ratelimit.PublicLinkAuditor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static com.mamoki.ieojuda.domain.audit.entity.EmailType.DEATH_REPORT_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// issue #41 - 역할 수락 시 사망 신고 화면 링크를 담은 이메일이 함께 발송되는지, 그리고 그 토큰의
// 만료 시각이 다른 토큰과 달리 사실상 무기한(100년) 수준으로 길게 설정되는지 검증한다.
class ConfirmerInviteServiceTest {

    private PublicLinkAuditor publicLinkAuditor;
    private SecurityTokenService securityTokenService;
    private EmailOutboxService emailOutboxService;
    private AppProperties appProperties;
    private ConfirmerInviteService confirmerInviteService;

    @BeforeEach
    void setUp() {
        publicLinkAuditor = mock(PublicLinkAuditor.class);
        securityTokenService = mock(SecurityTokenService.class);
        emailOutboxService = mock(EmailOutboxService.class);
        appProperties = mock(AppProperties.class);
        confirmerInviteService = new ConfirmerInviteService(appProperties, publicLinkAuditor, securityTokenService, emailOutboxService);

        when(appProperties.getBaseUrl()).thenReturn("https://ieoduda.example.com");
        when(appProperties.getContactEmail()).thenReturn("support@ieoduda.example.com");
    }

    private Confirmer pendingConfirmer(Plan plan) {
        return Confirmer.builder().plan(plan).name("확인자").relationship(Relationship.FRIEND).email("confirmer@test.com").build();
    }

    @Test
    void accept_sendsDeathReportEmailWithLinkAndFarFutureExpiry() {
        Plan plan = mock(Plan.class);
        Confirmer confirmer = pendingConfirmer(plan);

        SecurityToken acceptToken = mock(SecurityToken.class);
        when(acceptToken.getConfirmer()).thenReturn(confirmer);
        when(securityTokenService.resolve(eq("accept-token"), eq(SecurityTokenPurpose.ACCEPT_ROLE))).thenReturn(acceptToken);
        when(securityTokenService.issueForConfirmer(eq(SecurityTokenPurpose.REPORT_DEATH), eq(confirmer), eq(null), any()))
                .thenReturn("report-death-token");

        ConfirmerDecisionResponse response = confirmerInviteService.accept("accept-token", null);

        assertThat(response.reportDeathToken()).isEqualTo("report-death-token");

        ArgumentCaptor<EmailContent> contentCaptor = ArgumentCaptor.forClass(EmailContent.class);
        verify(emailOutboxService).enqueue(eq(plan), eq(null), eq(DEATH_REPORT_REQUEST), eq("confirmer@test.com"), contentCaptor.capture());
        assertThat(contentCaptor.getValue().body()).contains("https://ieoduda.example.com/death-report?token=report-death-token");

        ArgumentCaptor<LocalDateTime> expiresAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(securityTokenService).issueForConfirmer(eq(SecurityTokenPurpose.REPORT_DEATH), eq(confirmer), eq(null), expiresAtCaptor.capture());
        assertThat(expiresAtCaptor.getValue()).isAfter(LocalDateTime.now().plusYears(99));
    }

    @Test
    void decline_doesNotSendAnyEmail() {
        Plan plan = mock(Plan.class);
        Confirmer confirmer = pendingConfirmer(plan);

        SecurityToken acceptToken = mock(SecurityToken.class);
        when(acceptToken.getConfirmer()).thenReturn(confirmer);
        when(securityTokenService.resolve(eq("accept-token"), eq(SecurityTokenPurpose.ACCEPT_ROLE))).thenReturn(acceptToken);

        ConfirmerDecisionResponse response = confirmerInviteService.decline("accept-token", new ConfirmerDecisionRequest(null));

        assertThat(response.reportDeathToken()).isNull();
        verify(emailOutboxService, never()).enqueue(any(), any(), any(), any(), any());
    }
}
