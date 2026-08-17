package com.mamoki.ieojuda.domain.stage.service;

import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStageStatus;
import com.mamoki.ieojuda.domain.stage.repository.HandoverStageRepository;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxService;
import com.mamoki.ieojuda.global.security.PermissionGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// issue #51 - 핵심 버그 회귀 테스트: sendHandoffInvite는 더는 SMTP 결과와 무관하게 stage.send()나
// "발송됨" 기록을 하지 않는다. 실제 발송(및 성공 시 stage.send())은 EmailOutboxScheduler만 담당한다.
class HandoverStageServiceTest {

    private ReleaseCaseRepository releaseCaseRepository;
    private HandoverStageRepository handoverStageRepository;
    private RecipientRepository recipientRepository;
    private EmailOutboxService emailOutboxService;
    private AppProperties appProperties;
    private PermissionGuard permissionGuard;
    private HandoverStageService service;

    @BeforeEach
    void setUp() {
        releaseCaseRepository = mock(ReleaseCaseRepository.class);
        handoverStageRepository = mock(HandoverStageRepository.class);
        recipientRepository = mock(RecipientRepository.class);
        emailOutboxService = mock(EmailOutboxService.class);
        appProperties = mock(AppProperties.class);
        permissionGuard = mock(PermissionGuard.class);
        service = new HandoverStageService(releaseCaseRepository, handoverStageRepository,
                recipientRepository, emailOutboxService, appProperties, permissionGuard);

        when(appProperties.getInviteTokenTtlHours()).thenReturn(72L);
        when(appProperties.getBaseUrl()).thenReturn("https://example.com");
        when(appProperties.getContactEmail()).thenReturn("contact@example.com");
        when(handoverStageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createStagesAndDispatchFirst_enqueuesEmail_andNeverMarksStageSentSynchronously() {
        ReleaseCase releaseCase = mock(ReleaseCase.class);
        Plan plan = mock(Plan.class);
        when(releaseCase.getPlan()).thenReturn(plan);
        Recipient recipient = mock(Recipient.class);
        when(recipient.getEmail()).thenReturn("recipient@example.com");

        service.createStagesAndDispatchFirst(releaseCase, List.of(recipient));

        verify(recipient).issueInviteToken(anyString(), any());
        verify(emailOutboxService).enqueue(
                eq(plan), any(HandoverStage.class), eq(EmailType.POSTHUMOUS_HANDOFF_LINK),
                eq("recipient@example.com"), any(EmailContent.class));
        // 이 서비스는 발송 결과를 알 수 없으므로, 이 메서드가 끝난 뒤에도 단계는 여전히 PENDING이어야 한다
        // (과거 버그: SMTP 성공 여부와 무관하게 여기서 바로 SENT/sentAt을 기록했음).
        HandoverStage createdStage = captureCreatedStage();
        assertThat(createdStage.getStatus()).isEqualTo(HandoverStageStatus.PENDING);
        assertThat(createdStage.getSentAt()).isNull();
    }

    private HandoverStage captureCreatedStage() {
        var captor = org.mockito.ArgumentCaptor.forClass(HandoverStage.class);
        verify(emailOutboxService).enqueue(any(), captor.capture(), any(), anyString(), any());
        return captor.getValue();
    }
}
