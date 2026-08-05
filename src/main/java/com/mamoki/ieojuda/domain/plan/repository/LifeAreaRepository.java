package com.mamoki.ieojuda.domain.plan.repository;

import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LifeAreaRepository extends JpaRepository<LifeArea, Long> {
    List<LifeArea> findByPlan_PlanIdOrderByLifeIdAsc(Long planId);
}
