package com.mamoki.ieojuda.domain.audit.repository;

import com.mamoki.ieojuda.domain.audit.entity.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    // "이메일 발송 감사" 화면 - 사건에 속한 발송 이력 전체 조회 (plan 기준. 사건-plan은 1:1이라 plan_id로 충분)
    // issue #51 - sentAt은 미발송 건이면 null이라 정렬 기준으로 requestedAt(항상 채워짐) 사용
    List<EmailLog> findByPlan_PlanIdOrderByRequestedAtDesc(Long planId);
}
