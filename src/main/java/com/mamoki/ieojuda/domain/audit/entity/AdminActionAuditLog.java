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

// issue #59 - 사건 동결·파트너 배정·증빙 판정 같은 고위험 조작의 호출자와 결과를 감사할 수 있게 남기는 로그
@Entity
@Table(name = "admin_action_audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminActionAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", length = 50)
    private AdminActionType actionType;

    // 조작 대상(사건 ID, 증빙 ID 등) - 대상 종류마다 테이블이 달라서 ID만 남기고 종류는 actionType으로 유추한다
    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "success")
    private Boolean success;

    @Column(name = "detail", length = 255)
    private String detail;

    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    @Builder
    public AdminActionAuditLog(Long actorUserId, String actorEmail, AdminActionType actionType,
                                Long targetId, Boolean success, String detail) {
        this.actorUserId = actorUserId;
        this.actorEmail = actorEmail;
        this.actionType = actionType;
        this.targetId = targetId;
        this.success = success;
        this.detail = detail;
        this.occurredAt = LocalDateTime.now();
    }
}
