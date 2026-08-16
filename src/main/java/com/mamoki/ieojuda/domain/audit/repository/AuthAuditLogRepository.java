package com.mamoki.ieojuda.domain.audit.repository;

import com.mamoki.ieojuda.domain.audit.entity.AuthAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLog, Long> {

    // "인증 실패 감사" 화면 - 운영관리자가 최신순으로 조회
    Page<AuthAuditLog> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
