package com.mamoki.ieojuda.global.jwt.filter;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.entity.UserRole;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.global.jwt.component.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// issue #56 회귀 테스트 - 필터가 서명 검증만으로 끝내지 않고, 매 요청마다 DB의 현재 사용자 상태
// (존재 여부/정지 여부/tokenVersion)를 대조하며, 권한(role)도 토큰의 claim이 아니라 DB 값을 신뢰함을 검증한다.
class JwtAuthenticationFilterTest {

    private JwtTokenProvider jwtTokenProvider;
    private UserRepository userRepository;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        userRepository = mock(UserRepository.class);
        filter = new JwtAuthenticationFilter(jwtTokenProvider, userRepository);
        SecurityContextHolder.clearContext();

        when(jwtTokenProvider.isAccessToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("valid-token")).thenReturn(1L);
    }

    private User userWithRole(UserRole role) {
        User user = User.builder().email("u@test.com").password("hash").name("A").build();
        setField(user, "userId", 1L);
        setField(user, "role", role);
        return user;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    @Test
    void matchingTokenVersion_authenticatesWithDbRole_notJwtRoleClaim() throws Exception {
        User user = userWithRole(UserRole.ADMIN); // DB는 ADMIN
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.getTokenVersion("valid-token")).thenReturn(0);
        // 토큰 자체의 role claim은 USER로 낡아있다고 가정 - 그래도 DB(ADMIN)를 신뢰해야 한다
        when(jwtTokenProvider.getRole("valid-token")).thenReturn("USER");

        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(requestWithToken("valid-token"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_ADMIN");
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    void tokenVersionMismatch_isRejectedAsSessionRevoked() throws Exception {
        User user = userWithRole(UserRole.USER); // DB tokenVersion 기본값 0
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.getTokenVersion("valid-token")).thenReturn(99); // 옛날에 발급된 토큰(버전 낮음/다름)

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestWithToken("valid-token"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("SESSION_REVOKED");
        verify(chain, times(0)).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void suspendedUser_isRejectedEvenWithValidUnexpiredToken() throws Exception {
        User user = userWithRole(UserRole.USER);
        user.suspend();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.getTokenVersion("valid-token")).thenReturn(0);

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestWithToken("valid-token"), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("ACCOUNT_SUSPENDED");
        verify(chain, times(0)).doFilter(any(), any());
    }

    @Test
    void deletedUser_isRejected() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        when(jwtTokenProvider.getTokenVersion("valid-token")).thenReturn(0);

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestWithToken("valid-token"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, times(0)).doFilter(any(), any());
    }
}
