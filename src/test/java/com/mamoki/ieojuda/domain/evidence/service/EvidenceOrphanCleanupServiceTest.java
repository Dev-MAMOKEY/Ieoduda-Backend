package com.mamoki.ieojuda.domain.evidence.service;

import com.mamoki.ieojuda.domain.evidence.entity.EvidenceOrphanCleanup;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceOrphanCleanupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// issue #51 - registerCompensatingDelete의 즉시 삭제가 실패했을 때 재처리 대상으로 기록하는 지점.
class EvidenceOrphanCleanupServiceTest {

    private EvidenceOrphanCleanupRepository evidenceOrphanCleanupRepository;
    private EvidenceOrphanCleanupService service;

    @BeforeEach
    void setUp() {
        evidenceOrphanCleanupRepository = mock(EvidenceOrphanCleanupRepository.class);
        service = new EvidenceOrphanCleanupService(evidenceOrphanCleanupRepository);
    }

    @Test
    void recordOrphan_savesStorageKeyAndReason() {
        service.recordOrphan("evidence/1/uuid.pdf", "RuntimeException");

        ArgumentCaptor<EvidenceOrphanCleanup> captor = ArgumentCaptor.forClass(EvidenceOrphanCleanup.class);
        verify(evidenceOrphanCleanupRepository).save(captor.capture());
        EvidenceOrphanCleanup saved = captor.getValue();

        assertThat(saved.getStorageKey()).isEqualTo("evidence/1/uuid.pdf");
        assertThat(saved.getReason()).isEqualTo("RuntimeException");
        assertThat(saved.getDeletedAt()).isNull();
    }
}
