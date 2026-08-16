package com.mamoki.ieojuda.domain.stage.repository;

import com.mamoki.ieojuda.domain.stage.entity.Dependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DependencyRepository extends JpaRepository<Dependency, Long> {

    // 계획 스냅샷 생성 시 항목 간 선후행 관계를 함께 봉인하기 위한 조회
    List<Dependency> findByPlan_PlanId(Long planId);
}
