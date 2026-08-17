package com.mamoki.ieojuda.global.email.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    // EmailOutboxScheduler - 발송 대기 중인 행을 가져온다. EvidenceRepository의
    // findDueForDeletionForUpdateSkipLocked와 동일한 이유로 FOR UPDATE SKIP LOCKED + LIMIT 사용
    // (다중 인스턴스 중복 처리 방지, 한 배치가 잡는 잠금 개수 제한).
    @Query(value = "SELECT * FROM email_outbox " +
            "WHERE status = 'PENDING' " +
            "ORDER BY created_at " +
            "LIMIT 100 " +
            "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<EmailOutbox> findPendingForUpdateSkipLocked();
}
