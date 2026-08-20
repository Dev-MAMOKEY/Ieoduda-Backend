package com.mamoki.ieojuda.domain.evidence.service;

import com.mamoki.ieojuda.domain.evidence.entity.EvidenceOrphanCleanup;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceOrphanCleanupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// issue #51 - EvidenceSubmitService.registerCompensatingDelete의 즉시 삭제 시도가 실패했을 때 호출된다.
// 호출부(TransactionSynchronization.afterCompletion)는 원본 트랜잭션이 이미 끝난 뒤 실행되므로
// AdminActionAuditService.record와 같은 이유로 REQUIRES_NEW로 새 트랜잭션에 커밋한다.
@Service
@RequiredArgsConstructor
public class EvidenceOrphanCleanupService {

    private final EvidenceOrphanCleanupRepository evidenceOrphanCleanupRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOrphan(String storageKey, String reason) {
        evidenceOrphanCleanupRepository.save(EvidenceOrphanCleanup.builder()
                .storageKey(storageKey)
                .reason(reason)
                .build());
    }
}
