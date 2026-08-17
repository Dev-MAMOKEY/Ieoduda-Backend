package com.mamoki.ieojuda.global.jwt.filter;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.entity.UserStatus;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.jwt.component.JwtTokenProvider;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

// Authorization: Bearer {AT} 헤더를 검증해서 SecurityContext에 인증 정보(principal = userId)를 채운다.
// 여기서 던져지는 ExpiredJwtException/JwtException은 GlobalExceptionHandler(컨트롤러 이후 계층)가 못 잡으므로
// SecurityErrorResponseWriter로 직접 같은 포맷(RsData)의 401 응답을 만든다.
//
// issue #56 - 서명 검증만으로는 "권한이 이미 회수된 토큰"을 걸러낼 수 없다(만료 전까지 계속 유효했음).
// 그래서 매 요청마다 DB에서 사용자를 다시 조회해 (1) 계정이 존재하는지 (2) 정지 상태가 아닌지
// (3) 토큰이 실린 tokenVersion이 DB의 현재 값과 같은지 확인한다. 권한(role)도 토큰의 claim이 아니라
// DB의 현재 값을 신뢰한다 - 토큰 발급 이후 등급이 바뀌었다면 다음 요청부터 즉시 반영되어야 하기 때문이다.
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null) {
            try {
                if (!jwtTokenProvider.isAccessToken(token)) {
                    SecurityErrorResponseWriter.write(response, ErrorCode.TOKEN_INVALID);
                    return;
                }

                Long userId = jwtTokenProvider.getUserId(token);
                Integer tokenVersion = jwtTokenProvider.getTokenVersion(token);

                User user = userRepository.findById(userId).orElse(null);
                if (user == null) {
                    SecurityErrorResponseWriter.write(response, ErrorCode.TOKEN_INVALID);
                    return;
                }
                if (user.getStatus() == UserStatus.SUSPENDED) {
                    SecurityErrorResponseWriter.write(response, ErrorCode.ACCOUNT_SUSPENDED);
                    return;
                }
                if (!Objects.equals(user.getTokenVersion(), tokenVersion)) {
                    SecurityErrorResponseWriter.write(response, ErrorCode.SESSION_REVOKED);
                    return;
                }

                List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId, null, authorities));
            } catch (ExpiredJwtException e) {
                SecurityErrorResponseWriter.write(response, ErrorCode.TOKEN_EXPIRED);
                return;
            } catch (JwtException e) {
                SecurityErrorResponseWriter.write(response, ErrorCode.TOKEN_INVALID);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER_NAME);
        if (header != null && header.startsWith(TOKEN_PREFIX)) {
            return header.substring(TOKEN_PREFIX.length());
        }
        return null;
    }
}
