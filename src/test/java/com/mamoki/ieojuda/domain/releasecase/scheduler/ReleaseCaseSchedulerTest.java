package com.mamoki.ieojuda.domain.releasecase.scheduler;

import com.mamoki.ieojuda.domain.plan.dto.PlanSnapshotDto;
import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.ItemActionType;
import com.mamoki.ieojuda.domain.plan.entity.ItemStatus;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanVersion;
import com.mamoki.ieojuda.domain.plan.service.PlanSnapshotService;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCaseStatus;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.stage.service.HandoverStageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// issue #42 회귀 테스트 - 사건 생성 후 라이브 계획(항목/담당자)이 바뀌더라도, 스케줄러가 실제로
// 발송하는 담당자는 사건 생성 시점에 봉인된 스냅샷 안의 값이어야 한다(라이브 테이블은 아예 조회하지 않는다).
class ReleaseCaseSchedulerTest {

    private ReleaseCaseRepository releaseCaseRepository;
    private RecipientRepository recipientRepository;
    private PlanSnapshotService planSnapshotService;
    private HandoverStageService handoverStageService;
    private ReleaseCaseScheduler scheduler;

    @BeforeEach
    void setUp() {
        releaseCaseRepository = mock(ReleaseCaseRepository.class);
        recipientRepository = mock(RecipientRepository.class);
        planSnapshotService = mock(PlanSnapshotService.class);
        handoverStageService = mock(HandoverStageService.class);
        scheduler = new ReleaseCaseScheduler(releaseCaseRepository, recipientRepository, planSnapshotService, handoverStageService);
    }

    @Test
    void progressExpiredWaitingCases_dispatchesRecipientFromSealedSnapshot_notFromLiveTables() {
        // 사건 생성 시점에 봉인된 스냅샷: 항목이 recipientId=100번 담당자에게 배정되어 있었다
        PlanSnapshotDto.ItemSnapshot itemSnapshot = new PlanSnapshotDto.ItemSnapshot(
                1L, 100L, "target", "loc", "action", "title", "content", "precond",
                DisclosureScope.FAMILY, "excerpt", ItemStatus.APPROVED, 0, ItemActionType.TRANSFER);
        PlanSnapshotDto snapshot = new PlanSnapshotDto(1L, 7, List.of(itemSnapshot), List.of(), List.of());

        Plan plan = mock(Plan.class);
        when(plan.getOrderConfirmedAt()).thenReturn(LocalDateTime.now());

        PlanVersion planVersion = mock(PlanVersion.class);
        when(planVersion.getSnapshotData()).thenReturn("{\"frozen\":true}");

        ReleaseCase releaseCase = mock(ReleaseCase.class);
        when(releaseCase.getPlan()).thenReturn(plan);
        when(releaseCase.getPlanVersion()).thenReturn(planVersion);

        when(releaseCaseRepository.findByStatusAndFrozenFalseAndWaitingEndsAtLessThanEqual(
                any(ReleaseCaseStatus.class), any(LocalDateTime.class)))
                .thenReturn(List.of(releaseCase));
        when(planSnapshotService.deserialize("{\"frozen\":true}")).thenReturn(snapshot);

        Recipient recipientFromSnapshot = mock(Recipient.class);
        when(recipientRepository.findById(100L)).thenReturn(Optional.of(recipientFromSnapshot));

        scheduler.progressExpiredWaitingCases();

        // 스냅샷에 적힌 담당자(100번)만 조회해서 그대로 인계 대상으로 넘긴다 -
        // 사건이 열린 뒤 라이브 담당자가 다른 사람으로 재배정됐어도 그 값은 절대 읽지 않는다.
        verify(recipientRepository).findById(100L);
        verify(handoverStageService).createStagesAndDispatchFirst(releaseCase, List.of(recipientFromSnapshot));
        verify(releaseCase).startReleasing();
    }

    @Test
    void progressExpiredWaitingCases_skipsCase_whenOrderNotYetConfirmed() {
        Plan plan = mock(Plan.class);
        when(plan.getOrderConfirmedAt()).thenReturn(null);

        ReleaseCase releaseCase = mock(ReleaseCase.class);
        when(releaseCase.getPlan()).thenReturn(plan);

        when(releaseCaseRepository.findByStatusAndFrozenFalseAndWaitingEndsAtLessThanEqual(
                any(ReleaseCaseStatus.class), any(LocalDateTime.class)))
                .thenReturn(List.of(releaseCase));

        scheduler.progressExpiredWaitingCases();

        verify(releaseCase, never()).startReleasing();
        verifyNoInteractions(handoverStageService, planSnapshotService);
    }
}
