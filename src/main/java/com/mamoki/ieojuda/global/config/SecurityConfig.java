package com.mamoki.ieojuda.global.config;

import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.global.consent.filter.ConsentCheckFilter;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.jwt.component.JwtTokenProvider;
import com.mamoki.ieojuda.global.jwt.filter.JwtAuthenticationFilter;
import com.mamoki.ieojuda.global.jwt.filter.SecurityErrorResponseWriter;
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
            "/api/dispute-contacts/*/verify"
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
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

    // 프론트엔드가 아직 정해진 도메인 없이 여러 환경(로컬/배포)에서 접근하는 개발 단계라 전체 origin을 허용.
    // Authorization 헤더에 Bearer 토큰을 담아 보내는 방식이라 쿠키 기반 인증(allowCredentials)은 쓰지 않으므로
    // origin을 "*"로 열어도 자격 증명 노출 문제가 없다.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
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
                        .requestMatchers(ADMIN_ONLY_PATHS).hasRole("ADMIN")
                        .requestMatchers(ADMIN_OR_EXTERNAL_PATHS).hasAnyRole("ADMIN", "EXTERNAL")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new ConsentCheckFilter(userRepository), JwtAuthenticationFilter.class);

        return http.build();
    }
}
