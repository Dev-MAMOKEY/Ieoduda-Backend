package com.mamoki.ieojuda.domain.audit.repository;

import com.mamoki.ieojuda.domain.audit.entity.AdminActionAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminActionAuditLogRepository extends JpaRepository<AdminActionAuditLog, Long> {

    // "관리자 조작 감사" 화면 - 운영관리자가 최신순으로 조회
    Page<AdminActionAuditLog> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
