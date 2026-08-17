package com.mamoki.ieojuda.domain.evidence.scheduler;

import com.mamoki.ieojuda.domain.audit.entity.AdminActionType;
import com.mamoki.ieojuda.domain.audit.service.AdminActionAuditService;
import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceRepository;
import com.mamoki.ieojuda.global.storage.EvidenceStorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// 명세서 "증빙 삭제 감사" 화면 - 검토 완료일 기준 30일이 지난 증빙 원본을 자동으로 삭제한다.
// deleted_at IS NULL 조건 자체가 재시도 가드다 - 성공한 행은 다음 주기에 다시 잡히지 않고,
// 실패한 행은 계속 매칭되어 자동으로 재시도된다(issue #57 스케줄러 동시성 규약과 동일하게
// FOR UPDATE SKIP LOCKED로 다중 인스턴스 안전성을 확보).
@Slf4j
@Component
@RequiredArgsConstructor
public class EvidenceDeletionScheduler {

    private final EvidenceRepository evidenceRepository;
    private final EvidenceStorageClient evidenceStorageClient;
    private final AdminActionAuditService adminActionAuditService;

    // 10분마다 - 대기기간이 일 단위인 도메인 특성상 촘촘한 주기가 필요하지 않음
    @Scheduled(fixedRate = 600_000)
    @Transactional
    public void deleteExpiredEvidences() {
        List<Evidence> dueEvidences = evidenceRepository.findDueForDeletionForUpdateSkipLocked(LocalDateTime.now());

        for (Evidence evidence : dueEvidences) {
            deleteOne(evidence);
        }
    }

    // S3 삭제는 되돌릴 수 없으므로, 한 건에서 발생한 예외가 다른 건의 markDeleted()까지
    // 롤백시키지 않도록 여기서 넓게 잡아 삼킨다(CustomException 뿐 아니라 어떤 예외든).
    private void deleteOne(Evidence evidence) {
        try {
            evidenceStorageClient.delete(evidence.getStorageKey());
            evidence.markDeleted();
        } catch (Exception e) {
            String reason = truncate(e.getMessage());
            evidence.markDeleteFailed(reason);
            log.error("[Evidence Deletion Alert] evidenceId={}, storageKey={}, cause={}",
                    evidence.getEvidenceId(), evidence.getStorageKey(), e.getMessage(), e);
            adminActionAuditService.recordSystem(
                    AdminActionType.EVIDENCE_DELETE, evidence.getEvidenceId(), false, reason);
        }
    }

    // Evidence.failureReason 컬럼은 length=1000 - SDK 예외 메시지가 이보다 길 수 있어 잘라낸다.
    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
