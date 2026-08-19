package com.mamoki.ieojuda.domain.evidence.repository;

import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import com.mamoki.ieojuda.domain.evidence.entity.EvidenceReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {

    // "공식 증빙 자료 제출" 화면 - 사건당 최대 3개 제한 검증용
    long countByReleaseCase_CaseId(UUID caseId);

    // issue #45 - "이 확인자가 이 사건에 이미 냈는지" 중복 제출 방지용
    boolean existsByReleaseCase_CaseIdAndConfirmer_ConfirmId(UUID caseId, UUID confirmId);

    // issue #41 재설계 - 이 사건에 이미 제출된 "다른 확인자"의 증빙이 있는지 찾는다. 있으면 그 확인자가
    // 이 사건의 신고 짝(sibling)이라는 뜻이라, 그 시점에 두 사람의 신고 날짜를 비교해 매칭 판정을 내린다.
    Optional<Evidence> findFirstByReleaseCase_CaseIdAndConfirmer_ConfirmIdNot(UUID caseId, UUID confirmId);

    // issue #45 - "여러 증빙의 승인 정책" - 이 사건에서 승인된 증빙이 몇 건인지 세어, 매칭된 확인자 수와
    // 비교해 전부 승인됐는지 판단한다.
    long countByReleaseCase_CaseIdAndReviewStatus(UUID caseId, EvidenceReviewStatus reviewStatus);

    // 계정 삭제 시 이 계획에 제출된 증빙을 전부 지우기 위한 조회 (S3 원본도 같이 정리해야 함)
    List<Evidence> findByPlan_PlanId(UUID planId);

    // "증빙 삭제 감사" 스케줄러 - 삭제 예정일이 지났고 아직 삭제되지 않은 증빙을 찾는다.
    // ReleaseCaseScheduler와 같은 이유로 FOR UPDATE SKIP LOCKED를 써서 다중 인스턴스가 같은 행을
    // 중복 처리하지 않게 하고, LIMIT으로 한 배치가 잡는 행 잠금 개수를 제한한다(다음 주기에 이어서 처리).
    @Query(value = "SELECT * FROM evidences " +
            "WHERE deleted_at IS NULL AND delete_scheduled_at IS NOT NULL AND delete_scheduled_at <= :now " +
            "ORDER BY delete_scheduled_at " +
            "LIMIT 100 " +
            "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Evidence> findDueForDeletionForUpdateSkipLocked(@Param("now") LocalDateTime now);

    // "외부 파트너 증빙 검토" 목록 화면(issue #87) - 배정된 파트너와 무관하게 EVIDENCE_REVIEW 권한이 있으면
    // 전체 검토 대상을 볼 수 있다. status가 null이면 전체 상태를 조회한다.
    @Query("SELECT e FROM Evidence e " +
            "WHERE e.deletedAt IS NULL " +
            "AND (:status IS NULL OR e.reviewStatus = :status) " +
            "ORDER BY e.submittedAt ASC")
    List<Evidence> findAllByReviewStatus(@Param("status") EvidenceReviewStatus status);
}
