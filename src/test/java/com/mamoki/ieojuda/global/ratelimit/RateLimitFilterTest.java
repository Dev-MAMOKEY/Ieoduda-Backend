package com.mamoki.ieojuda.global.ratelimit;

import com.mamoki.ieojuda.domain.audit.service.AuthAuditService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// issue #55 회귀 테스트 - 규칙에 안 걸리는 경로는 그냥 통과하고(우회 확인), 걸리는 경로는 limit을 넘기면
// 429로 직접 응답하며 그 뒤 필터 체인(컨트롤러)까지 가지 않음을 검증한다.
class RateLimitFilterTest {

    private RateLimiter rateLimiter;
    private AuthAuditService authAuditService;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter();
        authAuditService = mock(AuthAuditService.class);
        List<RateLimitRule> rules = List.of(
                new RateLimitRule("login", "/auth/login", "POST", 2, Duration.ofMinutes(15))
        );
        filter = new RateLimitFilter(rateLimiter, rules, authAuditService);
    }

    private MockHttpServletRequest request(String method, String path, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setRemoteAddr(ip);
        return request;
    }

    @Test
    void pathNotMatchingAnyRule_passesThroughUntouched() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/plans/1", "1.1.1.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void withinLimit_passesThroughToChain() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("POST", "/auth/login", "1.2.3.4"), response, chain);
            assertThat(response.getStatus()).isNotEqualTo(429);
        }
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void exceedingLimit_blocksWith429AndNeverReachesChain() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 2; i++) {
            filter.doFilter(request("POST", "/auth/login", "5.6.7.8"), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse thirdResponse = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/auth/login", "5.6.7.8"), thirdResponse, chain);

        assertThat(thirdResponse.getStatus()).isEqualTo(429);
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void differentIps_areRateLimitedIndependently() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 2; i++) {
            filter.doFilter(request("POST", "/auth/login", "9.9.9.9"), new MockHttpServletResponse(), chain);
        }

        // 다른 IP는 완전히 별개 한도를 가지므로 여전히 통과해야 한다
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/auth/login", "10.10.10.10"), response, chain);

        assertThat(response.getStatus()).isNotEqualTo(429);
    }
}
