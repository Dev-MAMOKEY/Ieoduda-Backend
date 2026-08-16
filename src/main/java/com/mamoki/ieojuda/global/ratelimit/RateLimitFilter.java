package com.mamoki.ieojuda.global.ratelimit;

import com.mamoki.ieojuda.domain.audit.entity.AuthAuditEventType;
import com.mamoki.ieojuda.domain.audit.service.AuthAuditService;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.jwt.filter.SecurityErrorResponseWriter;
import com.mamoki.ieojuda.global.util.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// issue #55 - 로그인/회원가입/공개 토큰 링크(사망신고·증빙·이의제기 등)에 대한 경로별 rate limit.
// JwtAuthenticationFilter보다 앞단에서 IP 기준으로 막아, 인증/토큰 검증 로직까지 갈 필요 없이 조기 차단한다.
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final List<RateLimitRule> rules;
    private final AuthAuditService authAuditService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitFilter(RateLimiter rateLimiter, List<RateLimitRule> rules, AuthAuditService authAuditService) {
        this.rateLimiter = rateLimiter;
        this.rules = rules;
        this.authAuditService = authAuditService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getServletPath();

        for (RateLimitRule rule : rules) {
            if (matches(rule, request, path)) {
                String ip = ClientIpResolver.resolve(request);
                String key = rule.label() + ":" + ip;
                if (!rateLimiter.tryConsume(key, rule.limit(), rule.window())) {
                    authAuditService.record(null, ip, AuthAuditEventType.RATE_LIMIT_EXCEEDED, rule.label() + " " + path);
                    SecurityErrorResponseWriter.write(response, ErrorCode.TOO_MANY_REQUESTS);
                    return;
                }
                break; // 가장 먼저 매치된 규칙 하나만 적용
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean matches(RateLimitRule rule, HttpServletRequest request, String path) {
        boolean methodMatches = rule.method() == null || rule.method().equalsIgnoreCase(request.getMethod());
        return methodMatches && pathMatcher.match(rule.pattern(), path);
    }
}
