package com.mamoki.ieojuda.domain.audit.dto;

import com.mamoki.ieojuda.domain.audit.entity.AdminActionAuditLog;
import com.mamoki.ieojuda.domain.audit.entity.AdminActionType;

import java.util.UUID;
import java.time.LocalDateTime;

public record AdminActionAuditLogResponse(
        UUID logId,
        UUID actorUserId,
        String actorEmail,
        AdminActionType actionType,
        UUID targetId,
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
