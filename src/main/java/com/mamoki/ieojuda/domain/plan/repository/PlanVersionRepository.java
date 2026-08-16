package com.mamoki.ieojuda.domain.plan.repository;

import com.mamoki.ieojuda.domain.plan.entity.PlanVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanVersionRepository extends JpaRepository<PlanVersion, Long> {

    // 사망 신고 접수 시 다음 버전 번호를 매기기 위한 카운트
    long countByPlan_PlanId(Long planId);
}
