package com.mamoki.ieojuda.domain.audit.dto;

import com.mamoki.ieojuda.domain.audit.entity.AuthAuditEventType;
import com.mamoki.ieojuda.domain.audit.entity.AuthAuditLog;

import java.time.LocalDateTime;

public record AuthAuditLogResponse(
        Long logId,
        String email,
        String ipAddress,
        AuthAuditEventType eventType,
        String detail,
        LocalDateTime occurredAt
) {
    public static AuthAuditLogResponse from(AuthAuditLog log) {
        return new AuthAuditLogResponse(
                log.getLogId(), log.getEmail(), log.getIpAddress(), log.getEventType(), log.getDetail(), log.getOccurredAt());
    }
}
