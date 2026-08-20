package com.mamoki.ieojuda.domain.confirmer.service;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.confirmer.dto.DeathReportInviteResponse;
import com.mamoki.ieojuda.domain.confirmer.dto.DeathReportRequest;
import com.mamoki.ieojuda.domain.confirmer.dto.DeathReportResponse;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.entity.DisputeContact;
import com.mamoki.ieojuda.domain.confirmer.entity.Relationship;
import com.mamoki.ieojuda.domain.confirmer.entity.ReportStatus;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.confirmer.repository.DisputeContactRepository;
import com.mamoki.ieojuda.domain.plan.dto.PlanSnapshotDto;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanStatus;
import com.mamoki.ieojuda.domain.plan.entity.PlanVersion;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanVersionRepository;
import com.mamoki.ieojuda.domain.plan.service.PlanReadinessValidator;
import com.mamoki.ieojuda.domain.plan.service.PlanSnapshotService;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.releasecase.service.ReleaseCaseWarningService;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityToken;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxService;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.idempotency.service.IdempotencyGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// issue #41 재설계 - report()는 더 이상 매칭 판정을 하지 않는다. 신고를 받을 때마다 사건을 찾거나(재사용)
// 만들고, 이 확인자 몫의 증빙 제출 토큰을 즉시 발급해 응답에 담아 돌려주는지 검증한다.
// 매칭(MATCHED/MISMATCHED) 판정 자체는 EvidenceSubmitServiceTest에서 검증한다.
class DeathReportServiceTest {

    private static final UUID PLAN_ID = UUID.randomUUID();

    private ReleaseCaseRepository releaseCaseRepository;
    private PlanVersionRepository planVersionRepository;
    private PlanSnapshotService planSnapshotService;
    private IdempotencyGuard idempotencyGuard;
    private SecurityTokenService securityTokenService;
    private EmailOutboxService emailOutboxService;
    private AppProperties appProperties;
    private PlanReadinessValidator planReadinessValidator;
    private ReleaseCaseWarningService releaseCaseWarningService;
    private DeathReportService deathReportService;

    @BeforeEach
    void setUp() {
        releaseCaseRepository = mock(ReleaseCaseRepository.class);
        planVersionRepository = mock(PlanVersionRepository.class);
        planSnapshotService = mock(PlanSnapshotService.class);
        idempotencyGuard = mock(IdempotencyGuard.class);
        securityTokenService = mock(SecurityTokenService.class);
        emailOutboxService = mock(EmailOutboxService.class);
        appProperties = mock(AppProperties.class);
        planReadinessValidator = mock(PlanReadinessValidator.class);
        releaseCaseWarningService = mock(ReleaseCaseWarningService.class);
        deathReportService = new DeathReportService(
                releaseCaseRepository, planVersionRepository,
                planSnapshotService, idempotencyGuard, securityTokenService, emailOutboxService, appProperties,
                planReadinessValidator, releaseCaseWarningService);

        when(appProperties.getBaseUrl()).thenReturn("https://ieoduda.example.com");
        when(appProperties.getContactEmail()).thenReturn("support@ieoduda.example.com");
    }

    private Confirmer confirmerAcceptedOn(Plan plan, String name, String email) {
        Confirmer confirmer = Confirmer.builder().plan(plan).name(name).relationship(Relationship.FRIEND).email(email).build();
        confirmer.accept(null);
        return confirmer;
    }

    private SecurityToken reportDeathTokenFor(Confirmer confirmer) {
        SecurityToken token = mock(SecurityToken.class);
        when(token.getConfirmer()).thenReturn(confirmer);
        return token;
    }

    private void stubSnapshotCreation(Plan plan, long existingVersionCount) {
        when(planVersionRepository.countByPlan_PlanId(PLAN_ID)).thenReturn(existingVersionCount);
        PlanSnapshotDto snapshot = new PlanSnapshotDto(PLAN_ID, 7, List.of(), List.of());
        when(planSnapshotService.buildSnapshot(plan)).thenReturn(snapshot);
        when(planSnapshotService.serialize(snapshot)).thenReturn("{\"planId\":1}");
        when(planSnapshotService.hash("{\"planId\":1}")).thenReturn("deadbeef");
        when(planVersionRepository.save(any(PlanVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(releaseCaseRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getInvite_withValidToken_returnsConfirmerAndOwnerInfo() {
        Plan plan = mock(Plan.class);
        User owner = mock(User.class);
        when(plan.getUser()).thenReturn(owner);
        when(owner.getName()).thenReturn("김나무");

        Confirmer confirmer = confirmerAcceptedOn(plan, "유지민", "confirmer@test.com");
        SecurityToken reportDeathToken = reportDeathTokenFor(confirmer);
        LocalDateTime expiresAt = LocalDateTime.now().plusYears(100);
        when(reportDeathToken.getExpiresAt()).thenReturn(expiresAt);
        when(securityTokenService.resolve(eq("token-a"), eq(SecurityTokenPurpose.REPORT_DEATH))).thenReturn(reportDeathToken);

        DeathReportInviteResponse response = deathReportService.getInvite("token-a");

        assertThat(response.confirmerName()).isEqualTo("유지민");
        assertThat(response.ownerName()).isEqualTo("김나무");
        assertThat(response.reportStatus()).isEqualTo("NOT_REPORTED");
        assertThat(response.email()).isEqualTo("confirmer@test.com");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        assertThat(response.contactEmail()).isEqualTo("support@ieoduda.example.com");
    }

    // 이미 신고를 제출해 토큰이 소모된 링크로 재접속하면, 역할 수락 초대 조회와 동일하게 그대로 예외가 전파되어야 한다
    @Test
    void getInvite_withAlreadyUsedToken_propagatesException() {
        when(securityTokenService.resolve(eq("used-token"), eq(SecurityTokenPurpose.REPORT_DEATH)))
                .thenThrow(new CustomException(ErrorCode.ACCESS_LINK_ALREADY_USED));

        assertThatThrownBy(() -> deathReportService.getInvite("used-token"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_LINK_ALREADY_USED);
    }

    @Test
    void report_firstConfirmer_createsCaseSealsSnapshotAndReturnsEvidenceToken() {
        Plan plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(PLAN_ID);

        Confirmer confirmer = confirmerAcceptedOn(plan, "A", "a@test.com");
        SecurityToken reportDeathToken = reportDeathTokenFor(confirmer);
        when(securityTokenService.resolve(eq("token-a"), eq(SecurityTokenPurpose.REPORT_DEATH))).thenReturn(reportDeathToken);
        when(securityTokenService.issueForConfirmer(eq(SecurityTokenPurpose.UPLOAD_EVIDENCE), eq(confirmer), any(), any()))
                .thenReturn("evidence-token");

        // 상대 확인자가 아직 신고하지 않아 활성 사건이 없는 상태 - 첫 신고이므로 새로 만들어야 한다
        when(releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(PLAN_ID)).thenReturn(Optional.empty());
        stubSnapshotCreation(plan, 2L); // 이미 봉인된 버전이 2개 있었다고 가정 - 새 버전은 3번이어야 한다

        DeathReportResponse response = deathReportService.report("token-a", new DeathReportRequest(LocalDate.of(2026, 8, 15)), null);

        ArgumentCaptor<PlanVersion> versionCaptor = ArgumentCaptor.forClass(PlanVersion.class);
        verify(planVersionRepository).save(versionCaptor.capture());
        PlanVersion savedVersion = versionCaptor.getValue();

        assertThat(savedVersion.getVersionNum()).isEqualTo(3);
        assertThat(savedVersion.getSnapshotData()).isEqualTo("{\"planId\":1}");
        assertThat(savedVersion.getIsSealed()).isTrue();
        assertThat(savedVersion.getSnapshotHash()).isEqualTo("deadbeef");
        assertThat(savedVersion.getSealedAt()).isNotNull();

        // 매칭 판정은 더 이상 여기서 일어나지 않는다 - 상대가 없으므로 REPORTED 그대로다
        assertThat(confirmer.getReportStatus()).isEqualTo(ReportStatus.REPORTED);
        assertThat(response.evidenceUploadToken()).isEqualTo("evidence-token");

        verify(releaseCaseWarningService).sendAuthorCancelWarningOrFreeze(any());
        ArgumentCaptor<EmailContent> contentCaptor = ArgumentCaptor.forClass(EmailContent.class);
        verify(emailOutboxService).enqueue(eq(plan), eq(null), eq(EmailType.EVIDENCE_SUBMISSION_REQUEST), eq("a@test.com"), contentCaptor.capture());
        assertThat(contentCaptor.getValue().body()).contains("evidence-token");
    }

    // 두 번째 신고자는 첫 신고자가 만든 활성 사건을 그대로 재사용해야 한다 - 매칭 여부와 무관하게 새 사건을 만들지 않는다
    @Test
    void report_whenActiveCaseAlreadyExists_reusesItWithoutCreatingNewOne() {
        Plan plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(PLAN_ID);

        Confirmer confirmer = confirmerAcceptedOn(plan, "B", "b@test.com");
        SecurityToken reportDeathToken = reportDeathTokenFor(confirmer);
        when(securityTokenService.resolve(eq("token-b"), eq(SecurityTokenPurpose.REPORT_DEATH))).thenReturn(reportDeathToken);
        when(securityTokenService.issueForConfirmer(eq(SecurityTokenPurpose.UPLOAD_EVIDENCE), eq(confirmer), any(), any()))
                .thenReturn("evidence-token-b");

        ReleaseCase existingCase = mock(ReleaseCase.class);
        UUID existingCaseId = UUID.randomUUID();
        when(existingCase.getCaseId()).thenReturn(existingCaseId);
        when(existingCase.getCanceledAt()).thenReturn(null);
        when(releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(PLAN_ID)).thenReturn(Optional.of(existingCase));

        DeathReportResponse response = deathReportService.report("token-b", new DeathReportRequest(LocalDate.of(2026, 8, 15)), null);

        verify(releaseCaseRepository, never()).saveAndFlush(any());
        verify(planReadinessValidator, never()).validate(any());
        verify(securityTokenService).issueForConfirmer(eq(SecurityTokenPurpose.UPLOAD_EVIDENCE), eq(confirmer), eq(existingCase), any());
        assertThat(response.caseId()).isEqualTo(existingCaseId);
        assertThat(response.evidenceUploadToken()).isEqualTo("evidence-token-b");
    }

    // 두 확인자가 거의 동시에 첫 신고를 하면 DB 유니크 인덱스가 두 번째 saveAndFlush를 막는다 - 이건 정상적인
    // 경쟁 상황이므로 에러로 실패하지 않고, 먼저 만들어진 사건을 다시 찾아 재사용해야 한다.
    @Test
    void report_whenConcurrentCaseCreationRaces_reusesWinningCaseInsteadOfFailing() {
        Plan plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(PLAN_ID);

        Confirmer confirmer = confirmerAcceptedOn(plan, "A", "a@test.com");
        SecurityToken reportDeathToken = reportDeathTokenFor(confirmer);
        when(securityTokenService.resolve(eq("token-a"), eq(SecurityTokenPurpose.REPORT_DEATH))).thenReturn(reportDeathToken);
        when(securityTokenService.issueForConfirmer(eq(SecurityTokenPurpose.UPLOAD_EVIDENCE), eq(confirmer), any(), any()))
                .thenReturn("evidence-token");

        ReleaseCase winningCase = mock(ReleaseCase.class);
        // 최초 조회 때는 아직 활성 사건이 없다고 보고했다가(경쟁 상대가 방금 만든 참), 저장 충돌 이후
        // 재조회에서는 경쟁 상대가 이미 만든 사건을 돌려준다.
        when(releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(PLAN_ID))
                .thenReturn(Optional.empty(), Optional.of(winningCase));
        when(winningCase.getCanceledAt()).thenReturn(null);

        stubSnapshotCreation(plan, 0L);
        when(releaseCaseRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique violation"));

        DeathReportResponse response = deathReportService.report("token-a", new DeathReportRequest(LocalDate.of(2026, 8, 15)), null);

        verify(securityTokenService).issueForConfirmer(eq(SecurityTokenPurpose.UPLOAD_EVIDENCE), eq(confirmer), eq(winningCase), any());
        assertThat(response.evidenceUploadToken()).isEqualTo("evidence-token");
        // 경쟁 상대가 사건을 만들었을 뿐이므로, 취소 경고 메일은 이 요청에서 다시 보내지 않는다
        verify(releaseCaseWarningService, never()).sendAuthorCancelWarningOrFreeze(any());
    }

    // 작성자 경고 메일 발송이 실패해도(운영 검토로 동결될 뿐) report() 자체는 정상 응답해야 한다
    @Test
    void report_whenAuthorWarningSendFails_stillReturnsSuccessResponse() {
        Plan plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(PLAN_ID);

        Confirmer confirmer = confirmerAcceptedOn(plan, "A", "a@test.com");
        SecurityToken reportDeathToken = reportDeathTokenFor(confirmer);
        when(securityTokenService.resolve(eq("token-a"), eq(SecurityTokenPurpose.REPORT_DEATH))).thenReturn(reportDeathToken);
        when(securityTokenService.issueForConfirmer(eq(SecurityTokenPurpose.UPLOAD_EVIDENCE), any(), any(), any()))
                .thenReturn("evidence-token");
        when(releaseCaseWarningService.sendAuthorCancelWarningOrFreeze(any())).thenReturn(false);

        when(releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(PLAN_ID)).thenReturn(Optional.empty());
        stubSnapshotCreation(plan, 0L);

        var response = deathReportService.report("token-a", new DeathReportRequest(LocalDate.of(2026, 8, 15)), null);

        assertThat(response).isNotNull();
        verify(releaseCaseWarningService).sendAuthorCancelWarningOrFreeze(any());
    }

    // 준비도 검증 실패 시 사건이 생성되지 않아야 한다
    @Test
    void report_whenPlanNotReady_propagatesExceptionWithoutCreatingCase() {
        Plan plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(PLAN_ID);

        Confirmer confirmer = confirmerAcceptedOn(plan, "A", "a@test.com");
        SecurityToken reportDeathToken = reportDeathTokenFor(confirmer);
        when(securityTokenService.resolve(eq("token-a"), eq(SecurityTokenPurpose.REPORT_DEATH))).thenReturn(reportDeathToken);

        when(releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(PLAN_ID)).thenReturn(Optional.empty());
        doThrow(new CustomException(ErrorCode.PLAN_NOT_READY)).when(planReadinessValidator).validate(plan);

        assertThatThrownBy(() ->
                deathReportService.report("token-a", new DeathReportRequest(LocalDate.of(2026, 8, 15)), null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PLAN_NOT_READY);

        verify(releaseCaseRepository, never()).saveAndFlush(any());
    }

    // 검증기를 목이 아닌 실제 구현으로 붙여, "정규화 이메일 기준 서로 다른 확인자 2명 미만"이라는
    // INSUFFICIENT_CONFIRMERS 경로가 report() 레벨에서 실제로 사건 생성을 막는지 직접 재현한다.
    @Test
    void report_whenFewerThanTwoDistinctAcceptedConfirmers_throwsInsufficientConfirmersWithoutCreatingCase() {
        ConfirmerRepository readinessConfirmerRepository = mock(ConfirmerRepository.class);
        ItemRepository itemRepository = mock(ItemRepository.class);
        DisputeContactRepository disputeContactRepository = mock(DisputeContactRepository.class);
        PlanReadinessValidator realValidator =
                new PlanReadinessValidator(readinessConfirmerRepository, itemRepository, disputeContactRepository);
        deathReportService = new DeathReportService(
                releaseCaseRepository, planVersionRepository,
                planSnapshotService, idempotencyGuard, securityTokenService, emailOutboxService, appProperties,
                realValidator, releaseCaseWarningService);

        Plan plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(PLAN_ID);
        when(plan.getStatus()).thenReturn(PlanStatus.SEALED);
        when(plan.getWaitingDays()).thenReturn(14);
        when(plan.getSelfWarningEmailVerified()).thenReturn(true);
        when(plan.getOrderConfirmedAt()).thenReturn(LocalDateTime.now());

        Confirmer reporting = confirmerAcceptedOn(plan, "A", "same@test.com");
        SecurityToken reportDeathToken = reportDeathTokenFor(reporting);
        when(securityTokenService.resolve(eq("token-a"), eq(SecurityTokenPurpose.REPORT_DEATH))).thenReturn(reportDeathToken);

        Confirmer sibling = confirmerAcceptedOn(plan, "B", "SAME@test.com"); // 정규화하면 reporting과 같은 이메일

        when(releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(PLAN_ID)).thenReturn(Optional.empty());
        DisputeContact verifiedContact = DisputeContact.builder().plan(mock(Plan.class)).email("dispute@test.com").name("이의").build();
        verifiedContact.verify();
        when(disputeContactRepository.findFirstByPlan_PlanIdOrderByContactIdDesc(PLAN_ID)).thenReturn(Optional.of(verifiedContact));
        when(itemRepository.findByLifeArea_Plan_PlanIdOrderBySortOrderAscItemIdAsc(PLAN_ID)).thenReturn(List.of());
        // "정규화하면 같은 이메일"인 두 확인자만 존재 - distinct 기준으로는 1명뿐이라 INSUFFICIENT_CONFIRMERS여야 한다
        when(readinessConfirmerRepository.findByPlan_PlanIdOrderByConfirmIdAsc(PLAN_ID)).thenReturn(List.of(reporting, sibling));

        assertThatThrownBy(() ->
                deathReportService.report("token-a", new DeathReportRequest(LocalDate.of(2026, 8, 15)), null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_CONFIRMERS);

        verify(releaseCaseRepository, never()).saveAndFlush(any());
    }
}
