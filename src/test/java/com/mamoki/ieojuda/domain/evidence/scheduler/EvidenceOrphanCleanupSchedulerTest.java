package com.mamoki.ieojuda.domain.evidence.scheduler;

import com.mamoki.ieojuda.domain.audit.entity.AdminActionType;
import com.mamoki.ieojuda.domain.audit.service.AdminActionAuditService;
import com.mamoki.ieojuda.domain.evidence.entity.EvidenceOrphanCleanup;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceOrphanCleanupRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.storage.EvidenceStorageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// issue #51 - S3 보정 삭제 실패로 기록된 고아 객체를 재처리하는 워커. EvidenceDeletionSchedulerTest와
// 동일하게, 한 건의 실패가 다른 건의 성공을 막지 않아야 한다(배치 트랜잭션 하나로 묶여 있기 때문).
class EvidenceOrphanCleanupSchedulerTest {

    private EvidenceOrphanCleanupRepository evidenceOrphanCleanupRepository;
    private EvidenceStorageClient evidenceStorageClient;
    private AdminActionAuditService adminActionAuditService;
    private EvidenceOrphanCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        evidenceOrphanCleanupRepository = mock(EvidenceOrphanCleanupRepository.class);
        evidenceStorageClient = mock(EvidenceStorageClient.class);
        adminActionAuditService = mock(AdminActionAuditService.class);
        scheduler = new EvidenceOrphanCleanupScheduler(evidenceOrphanCleanupRepository, evidenceStorageClient, adminActionAuditService);
    }

    @Test
    void cleanupOrphans_whenNoneAreDue_doesNothing() {
        when(evidenceOrphanCleanupRepository.findPendingForUpdateSkipLocked()).thenReturn(List.of());

        scheduler.cleanupOrphans();

        verifyNoInteractions(evidenceStorageClient, adminActionAuditService);
    }

    @Test
    void cleanupOrphans_whenStorageDeleteSucceeds_marksDeletedWithoutAuditingSuccess() {
        EvidenceOrphanCleanup cleanup = mock(EvidenceOrphanCleanup.class);
        when(cleanup.getStorageKey()).thenReturn("evidence/1/uuid.pdf");
        when(evidenceOrphanCleanupRepository.findPendingForUpdateSkipLocked()).thenReturn(List.of(cleanup));

        scheduler.cleanupOrphans();

        verify(evidenceStorageClient).delete("evidence/1/uuid.pdf");
        verify(cleanup).markDeleted();
        verify(cleanup, never()).markDeleteFailed(anyString());
        verifyNoInteractions(adminActionAuditService);
    }

    @Test
    void cleanupOrphans_whenStorageDeleteFails_marksFailedAndAudits() {
        EvidenceOrphanCleanup cleanup = mock(EvidenceOrphanCleanup.class);
        when(cleanup.getCleanupId()).thenReturn(UUID.randomUUID());
        when(cleanup.getStorageKey()).thenReturn("evidence/5/uuid.pdf");
        when(evidenceOrphanCleanupRepository.findPendingForUpdateSkipLocked()).thenReturn(List.of(cleanup));
        doThrow(new CustomException(ErrorCode.EVIDENCE_STORAGE_FAILED))
                .when(evidenceStorageClient).delete("evidence/5/uuid.pdf");

        scheduler.cleanupOrphans();

        verify(cleanup, never()).markDeleted();
        verify(cleanup).markDeleteFailed(anyString());
        verify(adminActionAuditService)
                .recordSystem(eq(AdminActionType.EVIDENCE_ORPHAN_CLEANUP), any(UUID.class), eq(false), anyString());
    }

    @Test
    void cleanupOrphans_whenOneOfSeveralFails_theOthersStillGetDeleted() {
        EvidenceOrphanCleanup failing = mock(EvidenceOrphanCleanup.class);
        when(failing.getCleanupId()).thenReturn(UUID.randomUUID());
        when(failing.getStorageKey()).thenReturn("evidence/1/fail.pdf");
        doThrow(new RuntimeException("boom")).when(evidenceStorageClient).delete("evidence/1/fail.pdf");

        EvidenceOrphanCleanup succeeding = mock(EvidenceOrphanCleanup.class);
        when(succeeding.getCleanupId()).thenReturn(UUID.randomUUID());
        when(succeeding.getStorageKey()).thenReturn("evidence/2/ok.pdf");

        when(evidenceOrphanCleanupRepository.findPendingForUpdateSkipLocked())
                .thenReturn(List.of(failing, succeeding));

        scheduler.cleanupOrphans();

        verify(failing).markDeleteFailed(anyString());
        verify(succeeding).markDeleted();
        verify(succeeding, never()).markDeleteFailed(anyString());
    }

    // 재처리 멱등성: 첫 주기에 성공한 행은 deleted_at이 채워져 status='PENDING' 조건에서 제외되므로
    // 두 번째 실행에서 다시 조회되지 않는다 - evidenceStorageClient.delete가 두 번 호출되지 않는다.
    @Test
    void cleanupOrphans_calledTwice_whenRowAlreadyDeleted_doesNotDeleteAgain() {
        EvidenceOrphanCleanup cleanup = mock(EvidenceOrphanCleanup.class);
        when(cleanup.getStorageKey()).thenReturn("evidence/1/uuid.pdf");
        when(evidenceOrphanCleanupRepository.findPendingForUpdateSkipLocked())
                .thenReturn(List.of(cleanup))
                .thenReturn(List.of());

        scheduler.cleanupOrphans();
        scheduler.cleanupOrphans();

        verify(evidenceStorageClient, org.mockito.Mockito.times(1)).delete(any());
    }
}
