package com.mamoki.ieojuda.domain.confirmer.service;

import com.mamoki.ieojuda.domain.confirmer.dto.DeathReportRequest;
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
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.releasecase.service.ReleaseCaseWarningService;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityToken;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxService;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.idempotency.service.IdempotencyGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

// issue #42 회귀 테스트 - 두 확인자의 신고가 매치되어 사건이 생성될 때, 그 시점의 계획 스냅샷이
// 실제로 직렬화·해시·봉인되고 버전 번호가 정확히 증가하는지 검증한다.
class DeathReportServiceTest {

    private static final UUID PLAN_ID = UUID.randomUUID();

    private ConfirmerRepository confirmerRepository;
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
        confirmerRepository = mock(ConfirmerRepository.class);
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
                confirmerRepository, releaseCaseRepository, planVersionRepository,
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

    @Test
    void report_whenSecondConfirmerMatchesFirst_sealsFreshSnapshotWithIncrementedVersion() {
        Plan plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(PLAN_ID);

        Confirmer reporting = confirmerAcceptedOn(plan, "A", "a@test.com");
        SecurityToken reportDeathToken = mock(SecurityToken.class);
        when(reportDeathToken.getConfirmer()).thenReturn(reporting);
        when(securityTokenService.resolve(eq("token-a"), eq(SecurityTokenPurpose.REPORT_DEATH))).thenReturn(reportDeathToken);
        when(securityTokenService.issueForConfirmer(eq(SecurityTokenPurpose.UPLOAD_EVIDENCE), any(), any(), any()))
                .thenReturn("evidence-token");

        Confirmer sibling = confirmerAcceptedOn(plan, "B", "b@test.com");
        sibling.report(LocalDate.of(2026, 8, 15)); // 이미 먼저 신고해서 REPORTED 상태
        when(confirmerRepository.findByPlan_PlanIdAndConfirmIdNotAndReportStatus(PLAN_ID, null, ReportStatus.REPORTED))
                .thenReturn(List.of(sibling));

        // 이 계획에 이미 봉인된 버전이 2개 있었다고 가정 - 새 버전은 3번이어야 한다
        when(releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(PLAN_ID)).thenReturn(Optional.empty());
        when(planVersionRepository.countByPlan_PlanId(PLAN_ID)).thenReturn(2L);

        PlanSnapshotDto snapshot = new PlanSnapshotDto(PLAN_ID, 7, List.of(), List.of());
        when(planSnapshotService.buildSnapshot(plan)).thenReturn(snapshot);
        when(planSnapshotService.serialize(snapshot)).thenReturn("{\"planId\":1}");
        when(planSnapshotService.hash("{\"planId\":1}")).thenReturn("deadbeef");

        when(planVersionRepository.save(any(PlanVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(releaseCaseRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        deathReportService.report("token-a", new DeathReportRequest(LocalDate.of(2026, 8, 15)), null);

        ArgumentCaptor<PlanVersion> versionCaptor = ArgumentCaptor.forClass(PlanVersion.class);
        verify(planVersionRepository).save(versionCaptor.capture());
        PlanVersion savedVersion = versionCaptor.getValue();

        assertThat(savedVersion.getVersionNum()).isEqualTo(3);
        assertThat(savedVersion.getSnapshotData()).isEqualTo("{\"planId\":1}");
        assertThat(savedVersion.getIsSealed()).isTrue();
        assertThat(savedVersion.getSnapshotHash()).isEqualTo("deadbeef");
        assertThat(savedVersion.getSealedAt()).isNotNull();

        assertThat(reporting.getReportStatus()).isEqualTo(ReportStatus.MATCHED);
        assertThat(sibling.getReportStatus()).isEqualTo(ReportStatus.MATCHED);
        // 사건 생성 직후 작성자에게 취소 경고 메일 발송을 시도했는지 확인
        verify(releaseCaseWarningService).sendAuthorCancelWarningOrFreeze(any());
    }

    // 작성자 경고 메일 발송이 실패해도(운영 검토로 동결될 뿐) report() 자체는 정상 응답해야 한다 -
    // 이미 성사된 두 확인자의 신고 매칭까지 되돌릴 이유가 없다.
    @Test
    void report_whenAuthorWarningSendFails_stillReturnsSuccessResponse() {
        Plan plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(PLAN_ID);

        Confirmer reporting = confirmerAcceptedOn(plan, "A", "a@test.com");
        SecurityToken reportDeathToken = mock(SecurityToken.class);
        when(reportDeathToken.getConfirmer()).thenReturn(reporting);
        when(securityTokenService.resolve(eq("token-a"), eq(SecurityTokenPurpose.REPORT_DEATH))).thenReturn(reportDeathToken);
        when(securityTokenService.issueForConfirmer(eq(SecurityTokenPurpose.UPLOAD_EVIDENCE), any(), any(), any()))
                .thenReturn("evidence-token");
        when(releaseCaseWarningService.sendAuthorCancelWarningOrFreeze(any())).thenReturn(false);

        Confirmer sibling = confirmerAcceptedOn(plan, "B", "b@test.com");
        sibling.report(LocalDate.of(2026, 8, 15));
        when(confirmerRepository.findByPlan_PlanIdAndConfirmIdNotAndReportStatus(PLAN_ID, null, ReportStatus.REPORTED))
                .thenReturn(List.of(sibling));

        when(releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(PLAN_ID)).thenReturn(Optional.empty());
        when(planVersionRepository.countByPlan_PlanId(PLAN_ID)).thenReturn(0L);
        PlanSnapshotDto snapshot = new PlanSnapshotDto(PLAN_ID, 7, List.of(), List.of());
        when(planSnapshotService.buildSnapshot(plan)).thenReturn(snapshot);
        when(planSnapshotService.serialize(snapshot)).thenReturn("{}");
        when(planSnapshotService.hash("{}")).thenReturn("hash");
        when(planVersionRepository.save(any(PlanVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(releaseCaseRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = deathReportService.report("token-a", new DeathReportRequest(LocalDate.of(2026, 8, 15)), null);

        assertThat(response).isNotNull();
        verify(releaseCaseWarningService).sendAuthorCancelWarningOrFreeze(any());
    }

    // 정책 변경 회귀 테스트 - 한쪽만 사망일을 모르는 경우 더 이상 자동 일치로 처리하지 않는다
    @Test
    void report_whenOnlyOneConfirmerKnowsDeathDate_marksBothMismatchedWithoutCreatingCase() {
        Plan plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(PLAN_ID);

        Confirmer reporting = confirmerAcceptedOn(plan, "A", "a@test.com");
        SecurityToken reportDeathToken = mock(SecurityToken.class);
        when(reportDeathToken.getConfirmer()).thenReturn(reporting);
        when(securityTokenService.resolve(eq("token-a"), eq(SecurityTokenPurpose.REPORT_DEATH))).thenReturn(reportDeathToken);

        Confirmer sibling = confirmerAcceptedOn(plan, "B", "b@test.com");
        sibling.report(LocalDate.of(2026, 8, 15)); // 날짜를 알고 먼저 신고
        when(confirmerRepository.findByPlan_PlanIdAndConfirmIdNotAndReportStatus(PLAN_ID, null, ReportStatus.REPORTED))
                .thenReturn(List.of(sibling));

        deathReportService.report("token-a", new DeathReportRequest(null), null); // 사망일을 모른 채 신고

        assertThat(reporting.getReportStatus()).isEqualTo(ReportStatus.MISMATCHED);
        assertThat(sibling.getReportStatus()).isEqualTo(ReportStatus.MISMATCHED);
        verify(releaseCaseRepository, never()).saveAndFlush(any());
    }

    // 정책 변경 회귀 테스트 - 둘 다 사망일을 모르는 경우도 더 이상 자동 일치로 처리하지 않는다
    @Test
    void report_whenBothConfirmersDoNotKnowDeathDate_marksBothMismatchedWithoutCreatingCase() {
        Plan plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(PLAN_ID);

        Confirmer reporting = confirmerAcceptedOn(plan, "A", "a@test.com");
        SecurityToken reportDeathToken = mock(SecurityToken.class);
        when(reportDeathToken.getConfirmer()).thenReturn(reporting);
        when(securityTokenService.resolve(eq("token-a"), eq(SecurityTokenPurpose.REPORT_DEATH))).thenReturn(reportDeathToken);

        Confirmer sibling = confirmerAcceptedOn(plan, "B", "b@test.com");
        sibling.report(null); // 사망일을 모른 채 먼저 신고
        when(confirmerRepository.findByPlan_PlanIdAndConfirmIdNotAndReportStatus(PLAN_ID, null, ReportStatus.REPORTED))
                .thenReturn(List.of(sibling));

        deathReportService.report("token-a", new DeathReportRequest(null), null);

        assertThat(reporting.getReportStatus()).isEqualTo(ReportStatus.MISMATCHED);
        assertThat(sibling.getReportStatus()).isEqualTo(ReportStatus.MISMATCHED);
        verify(releaseCaseRepository, never()).saveAndFlush(any());
    }

    // 둘 다 사망일을 명시했지만 서로 다른 고전적 불일치 케이스
    @Test
    void report_whenConfirmersReportDifferentDeathDates_marksBothMismatchedWithoutCreatingCase() {
        Plan plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(PLAN_ID);

        Confirmer reporting = confirmerAcceptedOn(plan, "A", "a@test.com");
        SecurityToken reportDeathToken = mock(SecurityToken.class);
        when(reportDeathToken.getConfirmer()).thenReturn(reporting);
        when(securityTokenService.resolve(eq("token-a"), eq(SecurityTokenPurpose.REPORT_DEATH))).thenReturn(reportDeathToken);

        Confirmer sibling = confirmerAcceptedOn(plan, "B", "b@test.com");
        sibling.report(LocalDate.of(2026, 8, 15));
        when(confirmerRepository.findByPlan_PlanIdAndConfirmIdNotAndReportStatus(PLAN_ID, null, ReportStatus.REPORTED))
                .thenReturn(List.of(sibling));

        deathReportService.report("token-a", new DeathReportRequest(LocalDate.of(2026, 8, 16)), null);

        assertThat(reporting.getReportStatus()).isEqualTo(ReportStatus.MISMATCHED);
        assertThat(sibling.getReportStatus()).isEqualTo(ReportStatus.MISMATCHED);
        verify(releaseCaseRepository, never()).saveAndFlush(any());
    }

    // 준비도 검증 실패 시 두 신고가 일치하더라도 사건이 생성되지 않아야 한다
    @Test
    void report_whenPlanNotReady_propagatesExceptionWithoutCreatingCase() {
        Plan plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(PLAN_ID);

        Confirmer reporting = confirmerAcceptedOn(plan, "A", "a@test.com");
        SecurityToken reportDeathToken = mock(SecurityToken.class);
        when(reportDeathToken.getConfirmer()).thenReturn(reporting);
        when(securityTokenService.resolve(eq("token-a"), eq(SecurityTokenPurpose.REPORT_DEATH))).thenReturn(reportDeathToken);

        Confirmer sibling = confirmerAcceptedOn(plan, "B", "b@test.com");
        sibling.report(LocalDate.of(2026, 8, 15));
        when(confirmerRepository.findByPlan_PlanIdAndConfirmIdNotAndReportStatus(PLAN_ID, null, ReportStatus.REPORTED))
                .thenReturn(List.of(sibling));

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
                confirmerRepository, releaseCaseRepository, planVersionRepository,
                planSnapshotService, idempotencyGuard, securityTokenService, emailOutboxService, appProperties,
                realValidator, releaseCaseWarningService);

        Plan plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(PLAN_ID);
        when(plan.getStatus()).thenReturn(PlanStatus.SEALED);
        when(plan.getWaitingDays()).thenReturn(14);
        when(plan.getSelfWarningEmailVerified()).thenReturn(true);
        when(plan.getOrderConfirmedAt()).thenReturn(LocalDateTime.now());

        Confirmer reporting = confirmerAcceptedOn(plan, "A", "same@test.com");
        SecurityToken reportDeathToken = mock(SecurityToken.class);
        when(reportDeathToken.getConfirmer()).thenReturn(reporting);
        when(securityTokenService.resolve(eq("token-a"), eq(SecurityTokenPurpose.REPORT_DEATH))).thenReturn(reportDeathToken);

        Confirmer sibling = confirmerAcceptedOn(plan, "B", "SAME@test.com"); // 정규화하면 reporting과 같은 이메일
        sibling.report(LocalDate.of(2026, 8, 15));
        when(confirmerRepository.findByPlan_PlanIdAndConfirmIdNotAndReportStatus(PLAN_ID, null, ReportStatus.REPORTED))
                .thenReturn(List.of(sibling));

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
