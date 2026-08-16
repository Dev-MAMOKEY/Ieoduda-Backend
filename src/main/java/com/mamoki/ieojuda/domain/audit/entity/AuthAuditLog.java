package com.mamoki.ieojuda.domain.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// issue #55 - 고위험 인증 실패(로그인 실패/잠금, rate limit 초과)를 운영자가 추적할 수 있게 남기는 감사 로그
@Entity
@Table(name = "auth_audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    // 시도된 이메일 - 실제 존재하는 계정이 아닐 수도 있음(존재 여부를 노출하지 않기 위해 그대로 기록만 함)
    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 50)
    private AuthAuditEventType eventType;

    @Column(name = "detail", length = 255)
    private String detail;

    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    @Builder
    public AuthAuditLog(String email, String ipAddress, AuthAuditEventType eventType, String detail) {
        this.email = email;
        this.ipAddress = ipAddress;
        this.eventType = eventType;
        this.detail = detail;
        this.occurredAt = LocalDateTime.now();
    }
}
