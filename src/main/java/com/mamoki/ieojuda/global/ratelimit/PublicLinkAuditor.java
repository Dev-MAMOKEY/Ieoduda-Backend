package com.mamoki.ieojuda.global.ratelimit;

import com.mamoki.ieojuda.domain.audit.entity.AuthAuditEventType;
import com.mamoki.ieojuda.domain.audit.service.AuthAuditService;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// issue #55 - 공개 링크(사망신고/증빙/이의제기/역할 수락) 토큰은 진짜지만 상태가 안 맞아 실패하는 경우
// (만료/재사용/미검증/미수락 등)를 감사 로그에 남긴다. 존재하지 않는 토큰 추측은 TokenLookupGuard가 별도로 담당.
@Component
@RequiredArgsConstructor
public class PublicLinkAuditor {

    private final AuthAuditService authAuditService;
    private final HttpServletRequest httpServletRequest;

    public void recordStateFailure(ErrorCode errorCode) {
        authAuditService.record(null, ClientIpResolver.resolve(httpServletRequest),
                AuthAuditEventType.PUBLIC_LINK_STATE_FAILURE, errorCode.getCode());
    }
}
