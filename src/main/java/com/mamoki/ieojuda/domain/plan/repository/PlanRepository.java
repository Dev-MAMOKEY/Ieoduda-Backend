package com.mamoki.ieojuda.domain.plan.repository;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Long> {
}
