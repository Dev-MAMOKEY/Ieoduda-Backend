package com.mamoki.ieojuda.global.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// issue #55 "토큰별 실패 횟수와 잠금" - 공개 링크(사망신고/증빙/이의제기/역할 수락 등)에서 특정 토큰 값으로
// 반복 실패하면 그 토큰 자체를 잠근다. IP 기준 rate limit(RateLimitFilter)과는 다른 축 - IP를 바꿔가며
// 시도해도 "같은 토큰 값"을 계속 틀리게 쓰는 패턴은 여기서 잡힌다.
// LoginAttemptService와 알고리즘은 동일하지만, 계정(이메일)과 토큰은 서로 다른 키 공간이라 분리했다.
@Component
public class TokenAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean isLocked(String token) {
        Attempt attempt = attempts.get(token);
        if (attempt == null) {
            return false;
        }
        synchronized (attempt) {
            return attempt.lockedUntilMs > System.currentTimeMillis();
        }
    }

    // 반환값 - 이번 실패로 새로 잠금 상태에 진입했는지 여부
    public boolean recordFailure(String token) {
        Attempt attempt = attempts.computeIfAbsent(token, k -> new Attempt());
        synchronized (attempt) {
            long now = System.currentTimeMillis();
            if (now - attempt.windowStartMs >= FAILURE_WINDOW.toMillis()) {
                attempt.windowStartMs = now;
                attempt.failureCount = 0;
            }
            attempt.failureCount++;
            if (attempt.failureCount >= MAX_FAILURES) {
                attempt.lockedUntilMs = now + LOCK_DURATION.toMillis();
                return true;
            }
            return false;
        }
    }

    public void recordSuccess(String token) {
        attempts.remove(token);
    }

    private static final class Attempt {
        long windowStartMs = System.currentTimeMillis();
        int failureCount = 0;
        long lockedUntilMs = 0;
    }
}
