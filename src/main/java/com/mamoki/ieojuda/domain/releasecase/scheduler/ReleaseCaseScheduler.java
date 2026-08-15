package com.mamoki.ieojuda.domain.releasecase.scheduler;

import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCaseStatus;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.stage.service.HandoverStageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// 명세서 "사후 인계" 화면 - 대기 기간이 지나면 이의 제기 없이 자동으로 발송 단계에 진입시킨다
@Slf4j
@Component
@RequiredArgsConstructor
public class ReleaseCaseScheduler {

    private final ReleaseCaseRepository releaseCaseRepository;
    private final ItemRepository itemRepository;
    private final HandoverStageService handoverStageService;

    // 10분마다 - 대기기간이 긴(일 단위) 도메인 특성상 촘촘한 주기가 필요하지 않음
    @Scheduled(fixedRate = 600_000)
    @Transactional
    public void progressExpiredWaitingCases() {
        List<ReleaseCase> dueCases = releaseCaseRepository
                .findByStatusAndFrozenFalseAndWaitingEndsAtLessThanEqual(ReleaseCaseStatus.WAITING, LocalDateTime.now());

        for (ReleaseCase releaseCase : dueCases) {
            progressCase(releaseCase);
        }
    }

    private void progressCase(ReleaseCase releaseCase) {
        Long planId = releaseCase.getPlan().getPlanId();

        // 실행 순서가 확정 안 된 계획은 발송 순서를 정할 수 없으므로, 확정될 때까지 매 주기 재시도한다
        if (releaseCase.getPlan().getOrderConfirmedAt() == null) {
            log.warn("[ReleaseCase Dispatch Skipped] caseId={} - 실행 순서가 아직 확정되지 않았습니다.", releaseCase.getCaseId());
            return;
        }

        List<Item> items = itemRepository.findByLifeArea_Plan_PlanIdAndRecipientIsNotNullOrderBySortOrderAscItemIdAsc(planId);
        Set<Recipient> orderedRecipients = new LinkedHashSet<>();
        for (Item item : items) {
            orderedRecipients.add(item.getRecipient());
        }

        releaseCase.startReleasing();
        handoverStageService.createStagesAndDispatchFirst(releaseCase, List.copyOf(orderedRecipients));
    }
}
