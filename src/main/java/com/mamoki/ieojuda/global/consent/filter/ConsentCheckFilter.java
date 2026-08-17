package com.mamoki.ieojuda.global.consent.filter;

import java.util.UUID;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.entity.UserRole;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.jwt.filter.SecurityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// "사후 인계 안내" 필수 동의를 안 한 로그인 사용자는 동의 API 외 모든 보호된 API를 못 쓰게 막는다.
// JwtAuthenticationFilter가 SecurityContext에 채워둔 userId(principal)를 그대로 재사용하므로
// 반드시 JwtAuthenticationFilter 뒤에 등록해야 한다.
public class ConsentCheckFilter extends OncePerRequestFilter {

    // /auth/**, swagger 등 인증 자체가 필요 없는 경로와, 동의 API 자기 자신은 검사 대상에서 제외해야
    // 사용자가 동의 화면에 진입하고 동의를 제출할 수 있다.
    private static final String[] EXCLUDED_PATHS = {
            "/auth/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/api/self-warning-email/**",
            "/api/dispute-contacts/*/verify",
            // issue #94 - 동의 제출은 /api/consents로 옮겨졌다 (ConsentController).
            // /users/me/consent는 상태 조회(GET)만 남아있지만 계속 제외해야 조회 자체가 막히지 않는다.
            "/users/me/consent",
            "/api/consents"
    };

    private final UserRepository userRepository;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public ConsentCheckFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isExcluded(request.getServletPath())) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
            // 토큰이 없거나 유효하지 않은 요청은 이 필터의 관심사가 아님 - SecurityConfig의 인가 규칙이 처리
            filterChain.doFilter(request, response);
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            SecurityErrorResponseWriter.write(response, ErrorCode.CONSENT_REQUIRED);
            return;
        }

        // "사후 인계 안내" 동의는 계획 작성자(USER)에게만 의미 있는 절차 - 운영관리자/외부 파트너는 대상이 아님
        if (user.getRole() == UserRole.USER && user.getConsentAgreedAt() == null) {
            SecurityErrorResponseWriter.write(response, ErrorCode.CONSENT_REQUIRED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isExcluded(String path) {
        for (String pattern : EXCLUDED_PATHS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
