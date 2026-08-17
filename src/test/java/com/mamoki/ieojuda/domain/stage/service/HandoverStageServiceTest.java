package com.mamoki.ieojuda.domain.stage.service;

import com.mamoki.ieojuda.domain.account.entity.AdminPermission;
import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanVersion;
import com.mamoki.ieojuda.domain.postaccess.entity.AccessToken;
import com.mamoki.ieojuda.domain.postaccess.repository.AccessTokenRepository;
import com.mamoki.ieojuda.domain.postaccess.repository.PackageActionCompletionRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.entity.RoleType;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCaseStatus;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStageStatus;
import com.mamoki.ieojuda.domain.stage.repository.HandoverStageRepository;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxService;
import com.mamoki.ieojuda.global.email.contract.EmailSendResult;
import com.mamoki.ieojuda.global.email.sender.EmailSender;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.security.PermissionGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// issue #51 - 핵심 버그 회귀 테스트: sendHandoffInvite는 더는 SMTP 결과와 무관하게 stage.send()나
// "발송됨" 기록을 하지 않는다. 실제 발송(및 성공 시 stage.send())은 EmailOutboxScheduler만 담당한다.
// issue #78 - HandoverStage.complete()가 정의만 되고 호출되지 않아 발송 체인이 1단계에서 멈추던 문제.
// "전부 완료 -> 다음 단계 발송 -> 마지막이면 사건 완료", "BLOCKED는 다음 단계를 열지 않는다"를 검증한다.
class HandoverStageServiceTest {

    private static final UUID STAGE_ID = UUID.randomUUID();
    private static final UUID CASE_ID = UUID.randomUUID();

    private ReleaseCaseRepository releaseCaseRepository;
    private HandoverStageRepository handoverStageRepository;
    private RecipientRepository recipientRepository;
    private EmailOutboxService emailOutboxService;
    private AppProperties appProperties;
    private PermissionGuard permissionGuard;
    private PackageActionCompletionRepository packageActionCompletionRepository;
    private AccessTokenRepository accessTokenRepository;
    private SecurityTokenService securityTokenService;
    private HandoverStageService handoverStageService;

    private ReleaseCase releaseCase;
    private HandoverStage currentStage;

    @BeforeEach
    void setUp() {
        releaseCaseRepository = mock(ReleaseCaseRepository.class);
        handoverStageRepository = mock(HandoverStageRepository.class);
        recipientRepository = mock(RecipientRepository.class);
        emailOutboxService = mock(EmailOutboxService.class);
        appProperties = mock(AppProperties.class);
        permissionGuard = mock(PermissionGuard.class);
        packageActionCompletionRepository = mock(PackageActionCompletionRepository.class);
        accessTokenRepository = mock(AccessTokenRepository.class);
        securityTokenService = mock(SecurityTokenService.class);
        handoverStageService = new HandoverStageService(
                releaseCaseRepository, handoverStageRepository, recipientRepository, emailOutboxService,
                appProperties, permissionGuard, packageActionCompletionRepository, accessTokenRepository,
                securityTokenService);

        when(appProperties.getContactEmail()).thenReturn("support@ieoduda.example");
        when(appProperties.getInviteTokenTtlHours()).thenReturn(72L);
        when(appProperties.getBaseUrl()).thenReturn("https://ieoduda.example");
        when(handoverStageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(accessTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Plan plan = mock(Plan.class);
        PlanVersion planVersion = mock(PlanVersion.class);
        releaseCase = ReleaseCase.builder().plan(plan).planVersion(planVersion).build();
        setId(releaseCase, "caseId", CASE_ID);
        // issue #45 - ReleaseCase.complete()는 RELEASING에서만 허용되므로, 발송 단계 완료 흐름을
        // 검증하려면 사건을 실제로 그 상태까지 진행시켜둬야 한다(이 테스트의 관심사는 아니지만 전제 조건).
        releaseCase.confirmReport();
        releaseCase.awaitEvidence();
        releaseCase.startEvidenceReview();
        releaseCase.approveEvidenceAndStartWaiting(7);
        releaseCase.startReleasing();

        Recipient recipient = mock(Recipient.class);
        when(recipient.getEmail()).thenReturn("target@test.com");
        currentStage = HandoverStage.builder().plan(plan).recipient(recipient).stageOrder(0).build();
        currentStage.assignToCase(releaseCase);
        currentStage.send(); // 활성 발송 상태(SENT)에서 시작

        when(handoverStageRepository.findByIdForUpdate(STAGE_ID)).thenReturn(Optional.of(currentStage));
    }

    // 버그 회귀 방지(#79) - 이메일 링크에 박힌 평문 토큰이, 실제로 AccessToken 테이블에 그 해시로
    // 저장됐는지까지 끝까지 추적해서 확인한다. 링크 문자열만 바뀌고 발급처는 그대로였던 버그를 이 검증이 잡는다.
    private void assertLinkTokenWasIssuedAsAccessToken(EmailContent content) {
        Matcher matcher = Pattern.compile("/posthumous-access/([\\w-]+)").matcher(content.body());
        assertThat(matcher.find()).as("본문에 /posthumous-access/{token} 링크가 있어야 한다").isTrue();
        String plainToken = matcher.group(1);
        String expectedHash = TokenProvider.hashToken(plainToken);

        ArgumentCaptor<AccessToken> tokenCaptor = ArgumentCaptor.forClass(AccessToken.class);
        verify(accessTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getTokenHash()).isEqualTo(expectedHash);
    }

    private void setId(Object entity, String fieldName, UUID id) {
        try {
            var field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void createStagesAndDispatchFirst_enqueuesEmail_andNeverMarksStageSentSynchronously() {
        ReleaseCase localCase = mock(ReleaseCase.class);
        Plan plan = mock(Plan.class);
        when(localCase.getPlan()).thenReturn(plan);
        Recipient recipient = mock(Recipient.class);
        when(recipient.getEmail()).thenReturn("recipient@example.com");
        when(recipient.getRoleType()).thenReturn(RoleType.FAMILY_MANAGER);

        handoverStageService.createStagesAndDispatchFirst(localCase, List.of(recipient));

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
        var captor = ArgumentCaptor.forClass(HandoverStage.class);
        verify(emailOutboxService).enqueue(any(), captor.capture(), any(), anyString(), any());
        return captor.getValue();
    }

    @Test
    void completeStageIfAllActionsDone_whenNotAllCompleted_doesNothing() {
        when(packageActionCompletionRepository.countByHandoverStage_StageId(STAGE_ID)).thenReturn(1L);

        handoverStageService.completeStageIfAllActionsDone(STAGE_ID, 3);

        assertThat(currentStage.getStatus()).isNotEqualTo(HandoverStageStatus.COMPLETED);
        verify(emailOutboxService, never()).enqueue(any(), any(), any(), anyString(), any());
    }

    @Test
    void completeStageIfAllActionsDone_whenAllCompletedAndNextExists_completesAndDispatchesNext() {
        when(packageActionCompletionRepository.countByHandoverStage_StageId(STAGE_ID)).thenReturn(2L);

        Recipient nextRecipient = mock(Recipient.class);
        when(nextRecipient.getEmail()).thenReturn("next@test.com");
        when(nextRecipient.getRoleType()).thenReturn(RoleType.WORK_MANAGER);
        HandoverStage nextStage = HandoverStage.builder().plan(currentStage.getPlan()).recipient(nextRecipient).stageOrder(1).build();
        nextStage.assignToCase(releaseCase);
        when(handoverStageRepository.findFirstByReleaseCase_CaseIdAndStageOrderGreaterThanOrderByStageOrderAsc(any(), eq(0)))
                .thenReturn(Optional.of(nextStage));

        handoverStageService.completeStageIfAllActionsDone(STAGE_ID, 2);

        assertThat(currentStage.getStatus()).isEqualTo(HandoverStageStatus.COMPLETED);
        assertThat(nextStage.getStatus()).isEqualTo(HandoverStageStatus.PENDING); // 아직 발송은 워커가 처리 전
        assertThat(releaseCase.getStatus()).isNotEqualTo(ReleaseCaseStatus.COMPLETED);

        // issue #79 - 다음 단계로 자기 순서가 온 것뿐이니 대체 담당자 문구가 아니라 최초 발송 문구를 받아야 한다
        ArgumentCaptor<EmailContent> captor = ArgumentCaptor.forClass(EmailContent.class);
        verify(emailOutboxService).enqueue(any(), any(), any(), eq("next@test.com"), captor.capture());
        EmailContent content = captor.getValue();
        assertThat(content.body()).doesNotContain("대체 담당자로 지정되었습니다");
        assertThat(content.body()).contains("업무 담당자 역할로 전달드릴 내용이 있습니다");
        assertThat(content.body()).contains("https://ieoduda.example/posthumous-access/");
        assertThat(content.body()).doesNotContain("/recipient-acceptances/");
        assertLinkTokenWasIssuedAsAccessToken(content);
    }

    @Test
    void completeStageIfAllActionsDone_whenLastStage_completesReleaseCase() {
        when(packageActionCompletionRepository.countByHandoverStage_StageId(STAGE_ID)).thenReturn(1L);
        when(handoverStageRepository.findFirstByReleaseCase_CaseIdAndStageOrderGreaterThanOrderByStageOrderAsc(any(), any()))
                .thenReturn(Optional.empty());

        handoverStageService.completeStageIfAllActionsDone(STAGE_ID, 1);

        assertThat(currentStage.getStatus()).isEqualTo(HandoverStageStatus.COMPLETED);
        assertThat(releaseCase.getStatus()).isEqualTo(ReleaseCaseStatus.COMPLETED);
        assertThat(releaseCase.getCompletedAt()).isNotNull();
        verify(emailOutboxService, never()).enqueue(any(), any(), any(), anyString(), any());
    }

    @Test
    void completeStageIfAllActionsDone_whenAlreadyCompleted_doesNotDispatchAgain() {
        currentStage.complete(); // 동시 요청으로 이미 처리된 상황을 재현
        when(packageActionCompletionRepository.countByHandoverStage_StageId(STAGE_ID)).thenReturn(1L);

        handoverStageService.completeStageIfAllActionsDone(STAGE_ID, 1);

        verify(emailOutboxService, never()).enqueue(any(), any(), any(), anyString(), any());
        verify(handoverStageRepository, never())
                .findFirstByReleaseCase_CaseIdAndStageOrderGreaterThanOrderByStageOrderAsc(any(), any());
    }

    // issue #78 완료 조건 - "BLOCKED 상태에서는 다음 단계가 자동 활성화되지 않는다"
    @Test
    void completeStageIfAllActionsDone_whenStageBlocked_doesNotOpenNextStage() {
        currentStage.block();
        when(packageActionCompletionRepository.countByHandoverStage_StageId(STAGE_ID)).thenReturn(1L);

        handoverStageService.completeStageIfAllActionsDone(STAGE_ID, 1);

        assertThat(currentStage.getStatus()).isEqualTo(HandoverStageStatus.BLOCKED);
        verify(emailOutboxService, never()).enqueue(any(), any(), any(), anyString(), any());
        verify(handoverStageRepository, never())
                .findFirstByReleaseCase_CaseIdAndStageOrderGreaterThanOrderByStageOrderAsc(any(), any());
    }

    // issue #79 완료 조건 - "1단계 담당자가 대체 담당자 문구를 받지 않는다" + 사후 인증 화면 링크로 연결
    @Test
    void createStagesAndDispatchFirst_sendsInitialWordingNotFallbackWording() {
        Recipient firstRecipient = mock(Recipient.class);
        when(firstRecipient.getEmail()).thenReturn("first@test.com");
        when(firstRecipient.getRoleType()).thenReturn(RoleType.FAMILY_MANAGER);

        handoverStageService.createStagesAndDispatchFirst(releaseCase, List.of(firstRecipient));

        ArgumentCaptor<EmailContent> captor = ArgumentCaptor.forClass(EmailContent.class);
        verify(emailOutboxService).enqueue(any(), any(), any(), eq("first@test.com"), captor.capture());
        EmailContent content = captor.getValue();
        assertThat(content.subject()).doesNotContain("대체 담당자");
        assertThat(content.body()).doesNotContain("이전 담당자가 응답하지 않아");
        assertThat(content.body()).contains("가족 담당자 역할로 전달드릴 내용이 있습니다");
        assertThat(content.body()).contains("/posthumous-access/");
        assertLinkTokenWasIssuedAsAccessToken(content);
    }

    // issue #79 완료 조건 - "대체 담당자는 기존 문구를 그대로 받는다" + 링크만 사후 인증 화면으로 교체
    @Test
    void fallback_sendsFallbackWordingUnchangedButWithPosthumousAccessLink() {
        UUID userId = UUID.randomUUID(), caseId = UUID.randomUUID(), stageId = UUID.randomUUID();
        when(permissionGuard.require(userId, AdminPermission.CASE_SUPERVISE)).thenReturn(null);
        when(releaseCaseRepository.findById(caseId)).thenReturn(Optional.of(releaseCase));
        currentStage.assignToCase(releaseCase);
        when(handoverStageRepository.findById(stageId)).thenReturn(Optional.of(currentStage));

        Recipient backup = mock(Recipient.class);
        when(backup.getEmail()).thenReturn("backup@test.com");
        when(recipientRepository.findByBackupFor_AssigneeId(any())).thenReturn(Optional.of(backup));

        handoverStageService.fallback(userId, caseId, stageId);

        ArgumentCaptor<EmailContent> captor = ArgumentCaptor.forClass(EmailContent.class);
        verify(emailOutboxService).enqueue(any(), any(), any(), eq("backup@test.com"), captor.capture());
        EmailContent content = captor.getValue();
        assertThat(content.subject()).contains("대체 담당자");
        assertThat(content.body()).contains("이전 담당자가 응답하지 않아 대체 담당자로 지정되었습니다. 역할 수락 여부를 확인해 주세요.");
        assertThat(content.body()).contains("/posthumous-access/");
        assertThat(content.body()).doesNotContain("/recipient-acceptances/");
        assertLinkTokenWasIssuedAsAccessToken(content);
    }
}
