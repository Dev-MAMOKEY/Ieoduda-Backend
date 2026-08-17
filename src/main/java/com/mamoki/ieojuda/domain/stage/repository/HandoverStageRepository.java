package com.mamoki.ieojuda.domain.stage.repository;

import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HandoverStageRepository extends JpaRepository<HandoverStage, Long> {

    // 계정 삭제 시 이 계획의 인계 단계를 전부 지우기 위한 조회 (email_logs보다 나중, role_assignees/release_cases보다 먼저 지워야 함)
    List<HandoverStage> findByPlan_PlanId(Long planId);

    // issue #78 - 현재 단계가 완료됐을 때 그다음 순서로 발송할 담당자 단계를 찾기 위한 조회
    Optional<HandoverStage> findFirstByReleaseCase_CaseIdAndStageOrderGreaterThanOrderByStageOrderAsc(
            Long caseId, Integer stageOrder);

    // issue #78 - 같은 단계의 서로 다른 행동이 동시에 완료 요청되면 "전부 완료 판정 → 단계 완료 →
    // 다음 단계 발송"이 중복 실행될 수 있다. 비관적 잠금으로 이 단계에 대한 판정·전이를 직렬화한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from HandoverStage s where s.stageId = :stageId")
    Optional<HandoverStage> findByIdForUpdate(@Param("stageId") Long stageId);
}
