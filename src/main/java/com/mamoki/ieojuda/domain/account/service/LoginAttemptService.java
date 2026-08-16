package com.mamoki.ieojuda.domain.account.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// issue #55 - 계정(이메일) 단위 로그인 실패 추적 및 잠금. IP 단위 제한은 RateLimitFilter가 별도로 담당한다.
// 단일 인스턴스 배포를 전제로 메모리에 상태를 둔다(Redis 등 세션 저장소 도입은 이슈 범위 밖).
@Component
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean isLocked(String key) {
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return false;
        }
        synchronized (attempt) {
            return attempt.lockedUntilMs > System.currentTimeMillis();
        }
    }

    // 반환값 - 이번 실패로 새로 잠금 상태에 진입했는지 여부(감사 로그에 LOGIN_LOCKED로 구분 기록하기 위함)
    public boolean recordFailure(String key) {
        Attempt attempt = attempts.computeIfAbsent(key, k -> new Attempt());
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

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    private static final class Attempt {
        long windowStartMs = System.currentTimeMillis();
        int failureCount = 0;
        long lockedUntilMs = 0;
    }
}
