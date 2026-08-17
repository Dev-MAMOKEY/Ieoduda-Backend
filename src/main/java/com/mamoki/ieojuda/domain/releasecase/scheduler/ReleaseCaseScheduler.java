package com.mamoki.ieojuda.domain.releasecase.scheduler;

import com.mamoki.ieojuda.domain.plan.dto.PlanSnapshotDto;
import com.mamoki.ieojuda.domain.plan.service.PlanSnapshotService;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.stage.service.HandoverStageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.UUID;
import java.util.List;
import java.util.Objects;

// 명세서 "사후 인계" 화면 - 대기 기간이 지나면 이의 제기 없이 자동으로 발송 단계에 진입시킨다.
// 발송 대상·순서는 사건 생성 시점에 봉인된 계획 스냅샷(PlanVersion.snapshotData)만 읽는다 - 사건이 열린 뒤
// 사용자가 계획을 수정하더라도 이미 진행 중인 발송에는 영향을 주지 않기 위함(issue #42).
@Slf4j
@Component
@RequiredArgsConstructor
public class ReleaseCaseScheduler {

    private final ReleaseCaseRepository releaseCaseRepository;
    private final RecipientRepository recipientRepository;
    private final PlanSnapshotService planSnapshotService;
    private final HandoverStageService handoverStageService;

    // 10분마다 - 대기기간이 긴(일 단위) 도메인 특성상 촘촘한 주기가 필요하지 않음
    // FOR UPDATE SKIP LOCKED로 조회하므로, 다중 인스턴스가 동시에 돌아도 각 인스턴스는
    // 서로 다른 사건만 잠가서 처리한다 - 같은 사건이 중복으로 발송되지 않는다(issue #57).
    @Scheduled(fixedRate = 600_000)
    @Transactional
    public void progressExpiredWaitingCases() {
        List<ReleaseCase> dueCases = releaseCaseRepository
                .findDueCasesForUpdateSkipLocked(LocalDateTime.now());

        for (ReleaseCase releaseCase : dueCases) {
            progressCase(releaseCase);
        }
    }

    private void progressCase(ReleaseCase releaseCase) {
        // 실행 순서가 확정 안 된 계획은 발송 순서를 정할 수 없으므로, 확정될 때까지 매 주기 재시도한다
        if (releaseCase.getPlan().getOrderConfirmedAt() == null) {
            log.warn("[ReleaseCase Dispatch Skipped] caseId={} - 실행 순서가 아직 확정되지 않았습니다.", releaseCase.getCaseId());
            return;
        }

        PlanSnapshotDto snapshot = planSnapshotService.deserialize(releaseCase.getPlanVersion().getSnapshotData());

        List<UUID> orderedRecipientIds = snapshot.items().stream()
                .filter(item -> item.recipientId() != null)
                .sorted(Comparator.comparing(PlanSnapshotDto.ItemSnapshot::sortOrder))
                .map(PlanSnapshotDto.ItemSnapshot::recipientId)
                .distinct()
                .toList();

        List<Recipient> orderedRecipients = orderedRecipientIds.stream()
                .map(id -> recipientRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .toList();

        releaseCase.startReleasing();
        handoverStageService.createStagesAndDispatchFirst(releaseCase, orderedRecipients);
    }
}
