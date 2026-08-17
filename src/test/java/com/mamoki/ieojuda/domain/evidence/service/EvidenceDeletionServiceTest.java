package com.mamoki.ieojuda.domain.evidence.service;

import com.mamoki.ieojuda.domain.account.entity.AdminPermission;
import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.audit.service.AdminActionAuditService;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.evidence.dto.EvidenceDeletionStatusResponse;
import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import com.mamoki.ieojuda.domain.evidence.entity.EvidenceType;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.global.security.PermissionGuard;
import com.mamoki.ieojuda.global.storage.EvidenceStorageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// issue #80 완료 조건 - "삭제 후 GET /api/evidence/{id}/deletion-status에 실제 삭제 시각이 나타난다"
class EvidenceDeletionServiceTest {

    private EvidenceRepository evidenceRepository;
    private EvidenceStorageClient evidenceStorageClient;
    private PermissionGuard permissionGuard;
    private AdminActionAuditService adminActionAuditService;
    private EvidenceDeletionService evidenceDeletionService;

    @BeforeEach
    void setUp() {
        evidenceRepository = mock(EvidenceRepository.class);
        evidenceStorageClient = mock(EvidenceStorageClient.class);
        permissionGuard = mock(PermissionGuard.class);
        adminActionAuditService = mock(AdminActionAuditService.class);
        evidenceDeletionService = new EvidenceDeletionService(
                evidenceRepository, evidenceStorageClient, permissionGuard, adminActionAuditService);

        when(permissionGuard.require(any(), any())).thenReturn(mock(User.class));
    }

    @Test
    void getStatus_afterSchedulerMarkedDeleted_exposesActualDeletionTimestamp() {
        UUID evidenceId = UUID.randomUUID();
        Evidence evidence = Evidence.builder()
                .confirmer(mock(Confirmer.class)).plan(mock(Plan.class)).releaseCase(mock(ReleaseCase.class))
                .storageKey("evidence/7/proof.pdf").fileName("proof.pdf").mimeType("application/pdf")
                .integrityHash("hash-xyz").evidenceType(EvidenceType.DEATH_CERTIFICATE).build();
        evidence.approve();
        evidence.markDeleted(); // 스케줄러가 실제로 지운 상황을 재현
        when(evidenceRepository.findById(evidenceId)).thenReturn(Optional.of(evidence));

        EvidenceDeletionStatusResponse response = evidenceDeletionService.getStatus(UUID.randomUUID(), evidenceId);

        assertThat(response.deletedAt()).isNotNull();
        assertThat(response.integrityHash()).isEqualTo("hash-xyz");
        assertThat(response.overdue()).isFalse(); // 이미 삭제됐으므로 경보 대상 아님
        // issue #88 완료 조건 - 원본 삭제 후에도 감사 기록에 증빙 종류가 남아야 한다
        assertThat(response.evidenceType()).isEqualTo("DEATH_CERTIFICATE");
    }

    @Test
    void getStatus_whenScheduledButNotYetDeleted_reportsOverdue() {
        UUID evidenceId = UUID.randomUUID();
        Evidence evidence = Evidence.builder()
                .confirmer(mock(Confirmer.class)).plan(mock(Plan.class)).releaseCase(mock(ReleaseCase.class))
                .storageKey("evidence/8/proof.pdf").fileName("proof.pdf").mimeType("application/pdf")
                .integrityHash("hash-old").evidenceType(EvidenceType.DEATH_CERTIFICATE).build();
        evidence.approve(); // deleteScheduledAt = 지금 + 30일... 이므로 아직 미래 - overdue 아님을 함께 확인
        when(evidenceRepository.findById(evidenceId)).thenReturn(Optional.of(evidence));

        EvidenceDeletionStatusResponse response = evidenceDeletionService.getStatus(UUID.randomUUID(), evidenceId);

        assertThat(response.deletedAt()).isNull();
        assertThat(response.overdue()).isFalse();
    }
}
