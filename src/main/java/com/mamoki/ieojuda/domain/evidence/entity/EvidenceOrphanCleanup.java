package com.mamoki.ieojuda.domain.evidence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.time.LocalDateTime;

// issue #51 - DB 트랜잭션 롤백 시 S3 보정 삭제(EvidenceSubmitService.registerCompensatingDelete)
// 자체가 실패하는 경우를 위한 내구성 있는 재처리 큐. Evidence의 deleted_at 재시도 가드 패턴과 동일하게,
// deletedAt이 null인 행만 EvidenceOrphanCleanupScheduler가 계속 재시도한다.
@Entity
@Table(name = "evidence_orphan_cleanups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvidenceOrphanCleanup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "cleanup_id")
    private UUID cleanupId;

    @Column(name = "storage_key", length = 500, nullable = false)
    private String storageKey;

    // 정리가 필요해진 사유 (예: 보정 삭제 자체가 던진 예외의 클래스명)
    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Builder
    public EvidenceOrphanCleanup(String storageKey, String reason) {
        this.storageKey = storageKey;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }

    public void markDeleted() {
        this.deletedAt = LocalDateTime.now();
        this.failureReason = null;
    }

    public void markDeleteFailed(String failureReason) {
        this.failureReason = failureReason;
    }
}
