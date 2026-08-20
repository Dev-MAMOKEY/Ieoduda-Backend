package com.mamoki.ieojuda.global.email.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.List;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {

    // EmailOutboxScheduler - 발송 대기 중인 행을 가져온다. EvidenceRepository의
    // findDueForDeletionForUpdateSkipLocked와 동일한 이유로 FOR UPDATE SKIP LOCKED + LIMIT 사용
    // (다중 인스턴스 중복 처리 방지, 한 배치가 잡는 잠금 개수 제한).
    @Query(value = "SELECT * FROM email_outbox " +
            "WHERE status = 'PENDING' " +
            "ORDER BY created_at " +
            "LIMIT 100 " +
            "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<EmailOutbox> findPendingForUpdateSkipLocked();

    // 테스트 정리용 - EmailLog 삭제 전, 그 로그를 참조하는 아웃박스 행을 먼저 지운다. body가 @Lob이라
    // find+delete로 엔티티를 로드하면 별도 트랜잭션에서 lob stream에 접근할 수 없어 실패하므로 벌크 삭제로 처리한다.
    @Modifying
    @Query("DELETE FROM EmailOutbox eo WHERE eo.emailLog.logId = :logId")
    void deleteByEmailLog_LogId(@Param("logId") UUID logId);
}
