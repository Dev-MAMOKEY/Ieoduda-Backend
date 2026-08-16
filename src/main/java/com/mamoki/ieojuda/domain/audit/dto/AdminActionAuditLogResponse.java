package com.mamoki.ieojuda.domain.audit.dto;

import com.mamoki.ieojuda.domain.audit.entity.AdminActionAuditLog;
import com.mamoki.ieojuda.domain.audit.entity.AdminActionType;

import java.time.LocalDateTime;

public record AdminActionAuditLogResponse(
        Long logId,
        Long actorUserId,
        String actorEmail,
        AdminActionType actionType,
        Long targetId,
        Boolean success,
        String detail,
        LocalDateTime occurredAt
) {
    public static AdminActionAuditLogResponse from(AdminActionAuditLog log) {
        return new AdminActionAuditLogResponse(
                log.getLogId(), log.getActorUserId(), log.getActorEmail(), log.getActionType(),
                log.getTargetId(), log.getSuccess(), log.getDetail(), log.getOccurredAt());
    }
}
