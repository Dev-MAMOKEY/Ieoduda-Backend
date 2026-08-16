package com.mamoki.ieojuda.domain.confirmer.service;

import com.mamoki.ieojuda.domain.confirmer.dto.DeathReportRequest;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.entity.Relationship;
import com.mamoki.ieojuda.domain.confirmer.entity.ReportStatus;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.plan.dto.PlanSnapshotDto;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanVersion;
import com.mamoki.ieojuda.domain.plan.repository.PlanVersionRepository;
import com.mamoki.ieojuda.domain.plan.service.PlanSnapshotService;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// issue #42 회귀 테스트 - 두 확인자의 신고가 매치되어 사건이 생성될 때, 그 시점의 계획 스냅샷이
// 실제로 직렬화·해시·봉인되고 버전 번호가 정확히 증가하는지 검증한다.
class DeathReportServiceTest {

    private ConfirmerRepository confirmerRepository;
    private ReleaseCaseRepository releaseCaseRepository;
    private PlanVersionRepository planVersionRepository;
    private PlanSnapshotService planSnapshotService;
    private DeathReportService deathReportService;

    @BeforeEach
    void setUp() {
        confirmerRepository = mock(ConfirmerRepository.class);
        releaseCaseRepository = mock(ReleaseCaseRepository.class);
        planVersionRepository = mock(PlanVersionRepository.class);
        planSnapshotService = mock(PlanSnapshotService.class);
        deathReportService = new DeathReportService(
                confirmerRepository, releaseCaseRepository, planVersionRepository, planSnapshotService);
    }

    @Test
    void report_whenSecondConfirmerMatchesFirst_sealsFreshSnapshotWithIncrementedVersion() {
        Plan plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(1L);

        Confirmer reporting = Confirmer.builder().plan(plan).name("A").relationship(Relationship.FRIEND).email("a@test.com").build();
        reporting.accept(null);
        when(confirmerRepository.findByInviteToken(TokenProvider.hashToken("token-a"))).thenReturn(Optional.of(reporting));

        Confirmer sibling = Confirmer.builder().plan(plan).name("B").relationship(Relationship.FRIEND).email("b@test.com").build();
        sibling.accept(null);
        sibling.report(LocalDate.of(2026, 8, 15)); // 이미 먼저 신고해서 REPORTED 상태
        when(confirmerRepository.findByPlan_PlanIdAndConfirmIdNotAndReportStatus(1L, null, ReportStatus.REPORTED))
                .thenReturn(List.of(sibling));

        // 이 계획에 이미 봉인된 버전이 2개 있었다고 가정 - 새 버전은 3번이어야 한다
        when(releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(1L)).thenReturn(Optional.empty());
        when(planVersionRepository.countByPlan_PlanId(1L)).thenReturn(2L);

        PlanSnapshotDto snapshot = new PlanSnapshotDto(1L, 7, List.of(), List.of(), List.of());
        when(planSnapshotService.buildSnapshot(plan)).thenReturn(snapshot);
        when(planSnapshotService.serialize(snapshot)).thenReturn("{\"planId\":1}");
        when(planSnapshotService.hash("{\"planId\":1}")).thenReturn("deadbeef");

        when(planVersionRepository.save(any(PlanVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(releaseCaseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        deathReportService.report("token-a", new DeathReportRequest(LocalDate.of(2026, 8, 15)));

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
    }
}
