package com.mamoki.ieojuda.global.ratelimit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 경로별 rate limit(issue #55) - 고정 윈도우 카운터. 단일 인스턴스 배포를 전제로 메모리에 상태를 둔다
// (Redis 등 외부 세션 저장소 도입은 이슈 범위에서 명시적으로 제외됨).
@Component
public class RateLimiter {

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public boolean tryConsume(String key, int limit, Duration window) {
        Counter counter = counters.computeIfAbsent(key, k -> new Counter());
        synchronized (counter) {
            long now = System.currentTimeMillis();
            if (now - counter.windowStartMs >= window.toMillis()) {
                counter.windowStartMs = now;
                counter.count = 0;
            }
            counter.count++;
            return counter.count <= limit;
        }
    }

    // 오래된 카운터가 메모리에 계속 쌓이지 않도록 주기적으로 정리
    @Scheduled(fixedRate = 3_600_000)
    public void evictStaleCounters() {
        long cutoff = System.currentTimeMillis() - Duration.ofHours(2).toMillis();
        counters.entrySet().removeIf(entry -> entry.getValue().windowStartMs < cutoff);
    }

    private static final class Counter {
        volatile long windowStartMs = System.currentTimeMillis();
        int count = 0;
    }
}
