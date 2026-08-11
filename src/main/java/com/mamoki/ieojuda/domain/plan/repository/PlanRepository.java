package com.mamoki.ieojuda.domain.plan.repository;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlanRepository extends JpaRepository<Plan, Long> {
    // 1인 1계획 고정 - 로그인한 사용자가 자기 planId를 모를 때(예: 로그인 직후) 조회용
    Optional<Plan> findByUser_UserId(Long userId);
}
