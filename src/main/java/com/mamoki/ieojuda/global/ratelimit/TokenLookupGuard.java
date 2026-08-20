package com.mamoki.ieojuda.global.ratelimit;

import com.mamoki.ieojuda.domain.audit.entity.AuthAuditEventType;
import com.mamoki.ieojuda.domain.audit.service.AuthAuditService;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

// issue #55 - 공개 링크(사망신고/증빙/이의제기/역할 수락 등) 토큰 조회를 감싸서, 존재하지 않는 토큰이 반복
// 시도되면 그 토큰 자체를 잠그고 감사 로그를 남긴다. 각 서비스의 findByToken()이 이 메서드로 조회를 위임한다.
@Component
@RequiredArgsConstructor
public class TokenLookupGuard {

    private final TokenAttemptService tokenAttemptService;
    private final AuthAuditService authAuditService;
    private final HttpServletRequest httpServletRequest;

    public <T> T resolve(String plainToken, Supplier<Optional<T>> lookup) {
        String ip = ClientIpResolver.resolve(httpServletRequest);

        if (tokenAttemptService.isLocked(plainToken)) {
            authAuditService.record(null, ip, AuthAuditEventType.PUBLIC_LINK_TOKEN_LOCKED_ATTEMPT, null);
            throw new CustomException(ErrorCode.TOKEN_TEMPORARILY_LOCKED);
        }

        Optional<T> found = lookup.get();
        if (found.isEmpty()) {
            boolean nowLocked = tokenAttemptService.recordFailure(plainToken);
            authAuditService.record(null, ip,
                    nowLocked ? AuthAuditEventType.PUBLIC_LINK_TOKEN_LOCKED : AuthAuditEventType.PUBLIC_LINK_TOKEN_FAILURE, null);
            throw new CustomException(ErrorCode.TOKEN_INVALID);
        }

        tokenAttemptService.recordSuccess(plainToken);
        return found.get();
    }
}
