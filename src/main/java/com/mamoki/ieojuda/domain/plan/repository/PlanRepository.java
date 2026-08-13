package com.mamoki.ieojuda.domain.plan.repository;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlanRepository extends JpaRepository<Plan, Long> {
    // 1인 1계획 고정 - 로그인한 사용자가 자기 planId를 모를 때(예: 로그인 직후) 조회용
    Optional<Plan> findByUser_UserId(Long userId);

    // 본인 경고 이메일 검증 링크 클릭 시 토큰으로 계획을 찾기 위한 조회
    Optional<Plan> findBySelfWarningVerifyToken(String selfWarningVerifyToken);
}
