package com.mamoki.ieojuda.domain.stage.repository;

import com.mamoki.ieojuda.domain.stage.entity.Dependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DependencyRepository extends JpaRepository<Dependency, Long> {

    // 계정 삭제 시 이 계획의 항목 선후행 관계를 전부 지우기 위한 조회 (items보다 먼저 지워야 함)
    List<Dependency> findByPlan_PlanId(Long planId);
}
