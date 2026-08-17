package com.mamoki.ieojuda.domain.stage.service;

import com.mamoki.ieojuda.domain.audit.repository.EmailLogRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanVersion;
import com.mamoki.ieojuda.domain.postaccess.repository.PackageActionCompletionRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCaseStatus;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import com.mamoki.ieojuda.domain.stage.repository.HandoverStageRepository;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailSendResult;
import com.mamoki.ieojuda.global.email.sender.EmailSender;
import com.mamoki.ieojuda.global.security.PermissionGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// issue #78 - HandoverStage.complete()가 정의만 되고 호출되지 않아 발송 체인이 1단계에서 멈추던 문제.
// "전부 완료 -> 다음 단계 발송 -> 마지막이면 사건 완료", "BLOCKED는 다음 단계를 열지 않는다"를 검증한다.
class HandoverStageServiceTest {

    private ReleaseCaseRepository releaseCaseRepository;
    private HandoverStageRepository handoverStageRepository;
    private RecipientRepository recipientRepository;
    private EmailLogRepository emailLogRepository;
    private EmailSender emailSender;
    private AppProperties appProperties;
    private PermissionGuard permissionGuard;
    private PackageActionCompletionRepository packageActionCompletionRepository;
    private HandoverStageService handoverStageService;

    private ReleaseCase releaseCase;
    private HandoverStage currentStage;

    @BeforeEach
    void setUp() {
        releaseCaseRepository = mock(ReleaseCaseRepository.class);
        handoverStageRepository = mock(HandoverStageRepository.class);
        recipientRepository = mock(RecipientRepository.class);
        emailLogRepository = mock(EmailLogRepository.class);
        emailSender = mock(EmailSender.class);
        appProperties = mock(AppProperties.class);
        permissionGuard = mock(PermissionGuard.class);
        packageActionCompletionRepository = mock(PackageActionCompletionRepository.class);
        handoverStageService = new HandoverStageService(
                releaseCaseRepository, handoverStageRepository, recipientRepository, emailLogRepository,
                emailSender, appProperties, permissionGuard, packageActionCompletionRepository);

        when(appProperties.getContactEmail()).thenReturn("support@ieoduda.example");
        when(appProperties.getInviteTokenTtlHours()).thenReturn(72L);
        when(emailSender.send(anyString(), any())).thenReturn(EmailSendResult.success("msg-1"));
        when(emailLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Plan plan = mock(Plan.class);
        PlanVersion planVersion = mock(PlanVersion.class);
        releaseCase = ReleaseCase.builder().plan(plan).planVersion(planVersion).build();

        Recipient recipient = mock(Recipient.class);
        when(recipient.getEmail()).thenReturn("target@test.com");
        currentStage = HandoverStage.builder().plan(plan).recipient(recipient).stageOrder(0).build();
        currentStage.assignToCase(releaseCase);
        currentStage.send(); // 활성 발송 상태(SENT)에서 시작

        when(handoverStageRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(currentStage));
    }

    @Test
    void completeStageIfAllActionsDone_whenNotAllCompleted_doesNothing() {
        when(packageActionCompletionRepository.countByHandoverStage_StageId(1L)).thenReturn(1L);

        handoverStageService.completeStageIfAllActionsDone(1L, 3);

        assertThat(currentStage.getStatus()).isNotEqualTo(com.mamoki.ieojuda.domain.stage.entity.HandoverStageStatus.COMPLETED);
        verify(emailSender, never()).send(anyString(), any());
    }

    @Test
    void completeStageIfAllActionsDone_whenAllCompletedAndNextExists_completesAndDispatchesNext() {
        when(packageActionCompletionRepository.countByHandoverStage_StageId(1L)).thenReturn(2L);

        Recipient nextRecipient = mock(Recipient.class);
        when(nextRecipient.getEmail()).thenReturn("next@test.com");
        HandoverStage nextStage = HandoverStage.builder().plan(currentStage.getPlan()).recipient(nextRecipient).stageOrder(1).build();
        nextStage.assignToCase(releaseCase);
        when(handoverStageRepository.findFirstByReleaseCase_CaseIdAndStageOrderGreaterThanOrderByStageOrderAsc(any(), org.mockito.ArgumentMatchers.eq(0)))
                .thenReturn(Optional.of(nextStage));

        handoverStageService.completeStageIfAllActionsDone(1L, 2);

        assertThat(currentStage.getStatus()).isEqualTo(com.mamoki.ieojuda.domain.stage.entity.HandoverStageStatus.COMPLETED);
        assertThat(nextStage.getStatus()).isEqualTo(com.mamoki.ieojuda.domain.stage.entity.HandoverStageStatus.SENT);
        verify(emailSender).send(org.mockito.ArgumentMatchers.eq("next@test.com"), any());
        assertThat(releaseCase.getStatus()).isNotEqualTo(ReleaseCaseStatus.COMPLETED);
    }

    @Test
    void completeStageIfAllActionsDone_whenLastStage_completesReleaseCase() {
        when(packageActionCompletionRepository.countByHandoverStage_StageId(1L)).thenReturn(1L);
        when(handoverStageRepository.findFirstByReleaseCase_CaseIdAndStageOrderGreaterThanOrderByStageOrderAsc(any(), any()))
                .thenReturn(Optional.empty());

        handoverStageService.completeStageIfAllActionsDone(1L, 1);

        assertThat(currentStage.getStatus()).isEqualTo(com.mamoki.ieojuda.domain.stage.entity.HandoverStageStatus.COMPLETED);
        assertThat(releaseCase.getStatus()).isEqualTo(ReleaseCaseStatus.COMPLETED);
        assertThat(releaseCase.getCompletedAt()).isNotNull();
        verify(emailSender, never()).send(anyString(), any());
    }

    @Test
    void completeStageIfAllActionsDone_whenAlreadyCompleted_doesNotDispatchAgain() {
        currentStage.complete(); // 동시 요청으로 이미 처리된 상황을 재현
        when(packageActionCompletionRepository.countByHandoverStage_StageId(1L)).thenReturn(1L);

        handoverStageService.completeStageIfAllActionsDone(1L, 1);

        verify(emailSender, never()).send(anyString(), any());
        verify(handoverStageRepository, never())
                .findFirstByReleaseCase_CaseIdAndStageOrderGreaterThanOrderByStageOrderAsc(any(), any());
    }

    // issue #78 완료 조건 - "BLOCKED 상태에서는 다음 단계가 자동 활성화되지 않는다"
    @Test
    void completeStageIfAllActionsDone_whenStageBlocked_doesNotOpenNextStage() {
        currentStage.block();
        when(packageActionCompletionRepository.countByHandoverStage_StageId(1L)).thenReturn(1L);

        handoverStageService.completeStageIfAllActionsDone(1L, 1);

        assertThat(currentStage.getStatus()).isEqualTo(com.mamoki.ieojuda.domain.stage.entity.HandoverStageStatus.BLOCKED);
        verify(emailSender, never()).send(anyString(), any());
        verify(handoverStageRepository, never())
                .findFirstByReleaseCase_CaseIdAndStageOrderGreaterThanOrderByStageOrderAsc(any(), any());
    }
}
