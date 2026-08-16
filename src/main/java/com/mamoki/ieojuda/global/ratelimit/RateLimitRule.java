package com.mamoki.ieojuda.global.ratelimit;

import java.time.Duration;

// method가 null이면 메서드 무관하게 적용. RateLimitFilter가 위에서부터 순서대로 첫 매치만 적용하므로
// 더 구체적인(민감한) 경로를 먼저, 넓은 catch-all 경로를 나중에 등록해야 한다.
public record RateLimitRule(String label, String pattern, String method, int limit, Duration window) {
}
