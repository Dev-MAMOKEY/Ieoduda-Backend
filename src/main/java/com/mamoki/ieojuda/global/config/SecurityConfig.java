package com.mamoki.ieojuda.global.config;

import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.audit.service.AuthAuditService;
import com.mamoki.ieojuda.global.consent.filter.ConsentCheckFilter;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.jwt.component.JwtTokenProvider;
import com.mamoki.ieojuda.global.jwt.filter.JwtAuthenticationFilter;
import com.mamoki.ieojuda.global.jwt.filter.SecurityErrorResponseWriter;
import com.mamoki.ieojuda.global.ratelimit.RateLimitFilter;
import com.mamoki.ieojuda.global.ratelimit.RateLimitRule;
import com.mamoki.ieojuda.global.ratelimit.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PERMIT_ALL_PATHS = {
            "/auth/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            // 이메일 링크를 클릭해서 접근하는 검증 엔드포인트 - 로그인 상태가 아니므로 인증 요구하지 않음
            "/api/self-warning-email/**",
            "/api/dispute-contacts/*/verify",
            "/api/release-cases/*/disputes", // 이의 제기 접수 (초대 토큰이 곧 인증)
            "/api/release-cases/*/cancellations", // 경고 메일 취소 링크 접수 (CANCEL_CASE 토큰이 곧 인증)
            "/api/release-cases/*/waiting/status", // 경고 메일 waiting 링크 공개 조회 (CANCEL_CASE/RAISE_OBJECTION 토큰이 곧 인증)
            "/api/release-cases/*/evidence/**", // 증빙 제출 (초대 토큰이 곧 인증)
            "/api/recipient-acceptances/**",// 역할 담당자 수락
            "/api/confirmer-acceptances/**", // 지정확인자 수락 / 사망 신고
            "/api/posthumous-access/**", // 사후 인계 링크 검증 / OTP 발송·확인
            "/api/posthumous-packages/**", // 역할별 사후 패키지 조회 / 행동 완료 / 문제 신고 (열람 세션이 곧 인증)
            "/api/handoff-checks/**" // 선택형 생전 인계 점검 - 담당자 응답 (발송은 /api/plans/{planId}/handoff-checks라 별도 경로)
    };

    // 로그인한 작성자 본인 - 자기 계획의 대기 상태 조회 / 취소 (ADMIN_ONLY_PATHS의 /api/release-cases/**보다
    // 먼저 매칭되어야 하므로 filterChain에서 ADMIN_ONLY_PATHS 검사 앞에 등록한다)
    private static final String[] AUTHENTICATED_RELEASE_CASE_PATHS = {
            "/api/release-cases/*/waiting",
            "/api/release-cases/*/cancel"
    };

    // 운영관리자 전용 - 이메일 발송 감사/재시도/사건 동결/단계 조회·대체담당자 전환
    private static final String[] ADMIN_ONLY_PATHS = {
            "/api/admin/**",
            "/api/release-cases/**"
    };

    // 운영관리자 + 외부 파트너 공용 - 증빙 삭제 감사, 파트너 증빙 검토
    private static final String[] ADMIN_OR_EXTERNAL_PATHS = {
            "/api/evidence/**",
            "/api/partner/**"
    };

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RateLimiter rateLimiter;
    private final AuthAuditService authAuditService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // issue #55 - 경로별 rate limit 정책. 더 구체적인(민감한) 규칙을 넓은 catch-all 규칙보다 앞에 둔다.
    // 테스트 단계: 이메일 링크로 진입하는 공개 화면 관련 규칙만 limit을 임시로 5배 완화함. 운영 전환 시 원복 필요.
    private List<RateLimitRule> rateLimitRules() {
        return List.of(
                new RateLimitRule("signup", "/auth/signup", "POST", 5, Duration.ofHours(1)),
                new RateLimitRule("login", "/auth/login", "POST", 10, Duration.ofMinutes(15)),
                new RateLimitRule("refresh", "/auth/refresh", "POST", 20, Duration.ofMinutes(15)),
                new RateLimitRule("death-report", "/api/confirmer-acceptances/*/death-report", "POST", 50, Duration.ofHours(1)),
                new RateLimitRule("evidence-submit", "/api/release-cases/*/evidence/submit", "POST", 50, Duration.ofHours(1)),
                new RateLimitRule("objection", "/api/release-cases/*/disputes", "POST", 50, Duration.ofHours(1)),
                new RateLimitRule("case-cancellation", "/api/release-cases/*/cancellations", "POST", 50, Duration.ofHours(1)),
                new RateLimitRule("waiting-status", "/api/release-cases/*/waiting/status", "GET", 100, Duration.ofHours(1)),
                new RateLimitRule("dispute-verify", "/api/dispute-contacts/*/verify", null, 100, Duration.ofHours(1)),
                new RateLimitRule("dispute-contact-resend", "/api/dispute-contacts/*/verification-email", "POST", 25, Duration.ofHours(1)),
                new RateLimitRule("self-warning-email", "/api/self-warning-email/**", null, 100, Duration.ofHours(1)),
                new RateLimitRule("posthumous-otp", "/api/posthumous-access/*/otp", "POST", 25, Duration.ofHours(1)),
                new RateLimitRule("posthumous-verify", "/api/posthumous-access/*/verify", "POST", 50, Duration.ofHours(1)),
                new RateLimitRule("posthumous-link", "/api/posthumous-access/**", null, 150, Duration.ofHours(1)),
                new RateLimitRule("posthumous-package-complete", "/api/posthumous-packages/*/actions/*/complete", "POST", 100, Duration.ofHours(1)),
                new RateLimitRule("posthumous-package-issue", "/api/posthumous-packages/*/issues", "POST", 50, Duration.ofHours(1)),
                new RateLimitRule("posthumous-package", "/api/posthumous-packages/**", null, 300, Duration.ofHours(1)),
                new RateLimitRule("confirmer-link", "/api/confirmer-acceptances/**", null, 150, Duration.ofHours(1)),
                new RateLimitRule("recipient-link", "/api/recipient-acceptances/**", null, 150, Duration.ofHours(1)),
                new RateLimitRule("handoff-check-link", "/api/handoff-checks/**", null, 150, Duration.ofHours(1))
        );
    }

    // 토큰이 아예 없거나(익명) SecurityContext에 인증이 안 채워진 채로 보호된 API에 접근한 경우.
    // 토큰이 있었지만 만료/위조된 경우는 JwtAuthenticationFilter가 여기까지 오기 전에 직접 응답하고 끝낸다.
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) ->
                SecurityErrorResponseWriter.write(response, ErrorCode.TOKEN_INVALID);
    }

    // 인증은 됐지만 역할(role)이 안 맞는 경우(예: EXTERNAL이 /api/admin/** 호출) - 위 entry point와 달리 403으로 응답
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) ->
                SecurityErrorResponseWriter.write(response, ErrorCode.FORBIDDEN);
    }

    // 프론트엔드 배포 도메인이 확정되어(Vercel) origin을 그 도메인으로 제한한다.
    // 로컬 프론트 개발(Next.js 기본 포트)도 계속 동작하도록 함께 허용.
    // Authorization 헤더에 Bearer 토큰을 담아 보내는 방식이라 쿠키 기반 인증(allowCredentials)은 쓰지 않는다.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "https://ieoduda-frontend.vercel.app",
                "http://localhost:3000"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PERMIT_ALL_PATHS).permitAll()
                        .requestMatchers(AUTHENTICATED_RELEASE_CASE_PATHS).authenticated()
                        .requestMatchers(ADMIN_ONLY_PATHS).hasRole("ADMIN")
                        .requestMatchers(ADMIN_OR_EXTERNAL_PATHS).hasAnyRole("ADMIN", "EXTERNAL")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, userRepository), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new RateLimitFilter(rateLimiter, rateLimitRules(), authAuditService), JwtAuthenticationFilter.class)
                .addFilterAfter(new ConsentCheckFilter(userRepository), JwtAuthenticationFilter.class);

        return http.build();
    }
}
