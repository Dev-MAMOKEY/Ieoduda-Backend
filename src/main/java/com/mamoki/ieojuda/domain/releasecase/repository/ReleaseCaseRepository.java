package com.mamoki.ieojuda.domain.releasecase.repository;

import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCaseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReleaseCaseRepository extends JpaRepository<ReleaseCase, Long> {

    // 계획당 진행 중인 사건은 하나뿐 - 작성자 본인용 대기상태 조회, 사망 신고 시 중복 생성 방지에 사용
    Optional<ReleaseCase> findFirstByPlan_PlanIdOrderByCaseIdDesc(Long planId);

    // 스케줄러 - 대기 기간이 끝났고 동결되지 않은 사건을 찾기 위한 조회 (일반 조회용, 잠금 없음)
    List<ReleaseCase> findByStatusAndFrozenFalseAndWaitingEndsAtLessThanEqual(ReleaseCaseStatus status, LocalDateTime now);

    // 스케줄러 - 다중 인스턴스가 동시에 같은 사건을 처리하지 않도록 FOR UPDATE SKIP LOCKED로 조회.
    // 이미 다른 인스턴스가 잠근 행은 건너뛰고, 잠금을 잡은 사건만 이번 트랜잭션이 독점 처리한다.
    @Query(value = "SELECT * FROM release_cases " +
            "WHERE status = 'WAITING' AND frozen = false AND waiting_ends_at <= :now " +
            "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<ReleaseCase> findDueCasesForUpdateSkipLocked(@Param("now") LocalDateTime now);

    // 증빙 제출 - "개수 확인 후 저장" 사이의 경쟁 조건을 막기 위해 사건 행에 비관적 잠금을 건다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rc from ReleaseCase rc where rc.caseId = :caseId")
    Optional<ReleaseCase> findByIdForUpdate(@Param("caseId") Long caseId);

    // 계정 삭제 시 이 계획에 쌓인 사건(취소/완료된 과거 이력 포함)을 전부 지우기 위한 조회
    List<ReleaseCase> findByPlan_PlanId(Long planId);
}
