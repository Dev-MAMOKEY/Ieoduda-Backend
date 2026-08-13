package com.mamoki.ieojuda.domain.audit.repository;

import com.mamoki.ieojuda.domain.audit.entity.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    // "이메일 발송 감사" 화면 - 사건에 속한 발송 이력 전체 조회 (plan 기준. 사건-plan은 1:1이라 plan_id로 충분)
    List<EmailLog> findByPlan_PlanIdOrderBySentAtDesc(Long planId);
}
