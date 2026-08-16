package com.mamoki.ieojuda.domain.audit.service;

import com.mamoki.ieojuda.domain.audit.dto.AuthAuditLogResponse;
import com.mamoki.ieojuda.domain.audit.entity.AuthAuditEventType;
import com.mamoki.ieojuda.domain.audit.entity.AuthAuditLog;
import com.mamoki.ieojuda.domain.audit.repository.AuthAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// issue #55 - 고위험 인증 실패 감사 기록/조회.
// record()는 호출자(AuthService.login() 등)가 이어서 예외를 던지고 롤백되는 경우가 대부분이라,
// 그 롤백에 감사 기록까지 같이 사라지지 않도록 독립 트랜잭션(REQUIRES_NEW)으로 커밋한다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthAuditService {

    private final AuthAuditLogRepository authAuditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String email, String ipAddress, AuthAuditEventType eventType, String detail) {
        authAuditLogRepository.save(AuthAuditLog.builder()
                .email(email)
                .ipAddress(ipAddress)
                .eventType(eventType)
                .detail(detail)
                .build());
    }

    // "인증 실패 감사" 화면 - 운영관리자 전용
    public Page<AuthAuditLogResponse> getLogs(Pageable pageable) {
        return authAuditLogRepository.findAllByOrderByOccurredAtDesc(pageable).map(AuthAuditLogResponse::from);
    }
}
