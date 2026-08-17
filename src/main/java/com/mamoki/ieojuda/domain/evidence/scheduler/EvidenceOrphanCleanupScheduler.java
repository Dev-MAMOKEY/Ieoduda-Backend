package com.mamoki.ieojuda.domain.evidence.scheduler;

import com.mamoki.ieojuda.domain.audit.entity.AdminActionType;
import com.mamoki.ieojuda.domain.audit.service.AdminActionAuditService;
import com.mamoki.ieojuda.domain.evidence.entity.EvidenceOrphanCleanup;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceOrphanCleanupRepository;
import com.mamoki.ieojuda.global.storage.EvidenceStorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// issue #51 - DB 트랜잭션 롤백 시 EvidenceSubmitService의 즉시 보정 삭제가 실패해 기록된 S3 고아
// 객체를 재처리한다. EvidenceDeletionScheduler와 동일한 재시도 가드(deleted_at IS NULL) +
// FOR UPDATE SKIP LOCKED 다중 인스턴스 안전성 + 행 단위 예외 격리 패턴을 그대로 따른다.
@Slf4j
@Component
@RequiredArgsConstructor
public class EvidenceOrphanCleanupScheduler {

    private final EvidenceOrphanCleanupRepository evidenceOrphanCleanupRepository;
    private final EvidenceStorageClient evidenceStorageClient;
    private final AdminActionAuditService adminActionAuditService;

    // 10분마다 - EvidenceDeletionScheduler와 같은 주기
    @Scheduled(fixedRate = 600_000)
    @Transactional
    public void cleanupOrphans() {
        List<EvidenceOrphanCleanup> pending = evidenceOrphanCleanupRepository.findPendingForUpdateSkipLocked();

        for (EvidenceOrphanCleanup cleanup : pending) {
            cleanupOne(cleanup);
        }
    }

    private void cleanupOne(EvidenceOrphanCleanup cleanup) {
        try {
            evidenceStorageClient.delete(cleanup.getStorageKey());
            cleanup.markDeleted();
        } catch (Exception e) {
            String reason = truncate(e.getMessage());
            cleanup.markDeleteFailed(reason);
            log.error("[Evidence Orphan Cleanup Retry Failed] cleanupId={}, storageKey={}, cause={}",
                    cleanup.getCleanupId(), cleanup.getStorageKey(), e.getMessage(), e);
            adminActionAuditService.recordSystem(
                    AdminActionType.EVIDENCE_ORPHAN_CLEANUP, cleanup.getCleanupId(), false, reason);
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
