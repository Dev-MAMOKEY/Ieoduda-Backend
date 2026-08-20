package com.mamoki.ieojuda.domain.audit.service;

import java.util.UUID;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.audit.dto.AdminActionAuditLogResponse;
import com.mamoki.ieojuda.domain.audit.entity.AdminActionAuditLog;
import com.mamoki.ieojuda.domain.audit.entity.AdminActionType;
import com.mamoki.ieojuda.domain.audit.repository.AdminActionAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// issue #59 - 고위험 조작 감사 기록/조회.
// record()는 호출자가 이어서 예외를 던지고 롤백되는 경우에도(예: 재인증 실패) 시도 자체는 남아야 하므로
// AuthAuditService와 같은 이유로 독립 트랜잭션(REQUIRES_NEW)으로 커밋한다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminActionAuditService {

    private final AdminActionAuditLogRepository adminActionAuditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(User actor, AdminActionType actionType, UUID targetId, boolean success, String detail) {
        adminActionAuditLogRepository.save(AdminActionAuditLog.builder()
                .actorUserId(actor.getUserId())
                .actorEmail(actor.getEmail())
                .actionType(actionType)
                .targetId(targetId)
                .success(success)
                .detail(detail)
                .build());
    }

    // 스케줄러 등 사람 행위자가 없는 자동화된 고위험 조작(증빙 자동 삭제 실패 등) 기록용.
    // actor_user_id/actor_email 컬럼은 원래 nullable이라 스키마 변경 없이 사용 가능하다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSystem(AdminActionType actionType, UUID targetId, boolean success, String detail) {
        adminActionAuditLogRepository.save(AdminActionAuditLog.builder()
                .actionType(actionType)
                .targetId(targetId)
                .success(success)
                .detail(detail)
                .build());
    }

    // "관리자 조작 감사" 화면 - 운영관리자 전용
    public Page<AdminActionAuditLogResponse> getLogs(Pageable pageable) {
        return adminActionAuditLogRepository.findAllByOrderByOccurredAtDesc(pageable).map(AdminActionAuditLogResponse::from);
    }
}
