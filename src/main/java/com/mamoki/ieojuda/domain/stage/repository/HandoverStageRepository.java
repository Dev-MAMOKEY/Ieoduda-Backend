package com.mamoki.ieojuda.domain.stage.repository;

import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HandoverStageRepository extends JpaRepository<HandoverStage, Long> {

    // 계정 삭제 시 이 계획의 인계 단계를 전부 지우기 위한 조회 (email_logs보다 나중, role_assignees/release_cases보다 먼저 지워야 함)
    List<HandoverStage> findByPlan_PlanId(Long planId);
}
