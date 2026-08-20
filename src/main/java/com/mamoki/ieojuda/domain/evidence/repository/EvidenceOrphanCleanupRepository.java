package com.mamoki.ieojuda.domain.evidence.repository;

import com.mamoki.ieojuda.domain.evidence.entity.EvidenceOrphanCleanup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;
import java.util.List;

public interface EvidenceOrphanCleanupRepository extends JpaRepository<EvidenceOrphanCleanup, UUID> {

    // EvidenceOrphanCleanupScheduler - EvidenceRepository.findDueForDeletionForUpdateSkipLocked와
    // 동일한 이유로 FOR UPDATE SKIP LOCKED + LIMIT 사용 (다중 인스턴스 중복 처리 방지).
    @Query(value = "SELECT * FROM evidence_orphan_cleanups " +
            "WHERE deleted_at IS NULL " +
            "ORDER BY created_at " +
            "LIMIT 100 " +
            "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<EvidenceOrphanCleanup> findPendingForUpdateSkipLocked();
}
