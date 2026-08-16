package com.mamoki.ieojuda.global.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// issue #55 회귀 테스트 - rate limiter 경계값(정확히 limit개까지는 통과, 그 다음은 차단)과
// 서로 다른 키(다른 IP 등)는 완전히 독립적으로 계산됨을 검증한다.
class RateLimiterTest {

    private final RateLimiter rateLimiter = new RateLimiter();

    @Test
    void allowsExactlyUpToTheLimit_thenBlocksTheNextOne() {
        String key = "test-key-1";
        for (int i = 0; i < 3; i++) {
            assertThat(rateLimiter.tryConsume(key, 3, Duration.ofMinutes(1))).isTrue();
        }
        // 4번째 요청은 limit(3)을 넘으므로 차단
        assertThat(rateLimiter.tryConsume(key, 3, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void differentKeysAreCompletelyIndependent() {
        String keyA = "ip-a";
        String keyB = "ip-b";

        for (int i = 0; i < 3; i++) {
            assertThat(rateLimiter.tryConsume(keyA, 3, Duration.ofMinutes(1))).isTrue();
        }
        assertThat(rateLimiter.tryConsume(keyA, 3, Duration.ofMinutes(1))).isFalse();

        // keyA가 이미 소진됐어도 keyB는 완전히 별개로 3번 다 통과해야 한다
        for (int i = 0; i < 3; i++) {
            assertThat(rateLimiter.tryConsume(keyB, 3, Duration.ofMinutes(1))).isTrue();
        }
    }

    @Test
    void windowResetsAfterItExpires() throws InterruptedException {
        String key = "test-key-reset";
        Duration shortWindow = Duration.ofMillis(100);

        assertThat(rateLimiter.tryConsume(key, 1, shortWindow)).isTrue();
        assertThat(rateLimiter.tryConsume(key, 1, shortWindow)).isFalse();

        Thread.sleep(150);

        // 윈도우가 지났으므로 다시 처음부터 카운트되어 통과해야 한다
        assertThat(rateLimiter.tryConsume(key, 1, shortWindow)).isTrue();
    }

    @Test
    void limitOfZero_blocksEveryRequest() {
        String key = "test-key-zero";
        assertThat(rateLimiter.tryConsume(key, 0, Duration.ofMinutes(1))).isFalse();
    }
}
