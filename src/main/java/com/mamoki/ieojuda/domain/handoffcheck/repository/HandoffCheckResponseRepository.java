package com.mamoki.ieojuda.domain.handoffcheck.repository;

import com.mamoki.ieojuda.domain.handoffcheck.entity.HandoffCheckResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HandoffCheckResponseRepository extends JpaRepository<HandoffCheckResponse, Long> {

    // 계정 삭제 시 이 계획의 역할 점검 응답을 전부 지우기 위한 조회 (handoff_checks보다 먼저 지워야 함)
    List<HandoffCheckResponse> findByHandoffCheck_Plan_PlanId(Long planId);
}
