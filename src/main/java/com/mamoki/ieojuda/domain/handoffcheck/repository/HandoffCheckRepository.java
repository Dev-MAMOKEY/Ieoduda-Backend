package com.mamoki.ieojuda.domain.handoffcheck.repository;

import com.mamoki.ieojuda.domain.handoffcheck.entity.HandoffCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HandoffCheckRepository extends JpaRepository<HandoffCheck, Long> {

    // 계정 삭제 시 이 계획에 쌓인 역할 점검 발송 이력을 전부 지우기 위한 조회
    List<HandoffCheck> findByPlan_PlanId(Long planId);
}
