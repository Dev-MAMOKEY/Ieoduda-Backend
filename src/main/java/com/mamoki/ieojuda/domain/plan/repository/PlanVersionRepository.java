package com.mamoki.ieojuda.domain.plan.repository;

import com.mamoki.ieojuda.domain.plan.entity.PlanVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanVersionRepository extends JpaRepository<PlanVersion, Long> {

    // 계정 삭제 시 이 계획의 스냅샷 버전을 전부 지우기 위한 조회 (release_cases보다 나중에 지워야 함)
    List<PlanVersion> findByPlan_PlanId(Long planId);
}
