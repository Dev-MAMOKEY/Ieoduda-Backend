package com.mamoki.ieojuda.global.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// issue #55 회귀 테스트 - 특정 토큰 값으로 5회까지는 잠기지 않다가 5번째 실패에서 잠기고,
// 성공하면 초기화됨을 검증한다. 계정 잠금(LoginAttemptService)과 동일 알고리즘, 별개 키 공간.
class TokenAttemptServiceTest {

    private final TokenAttemptService tokenAttemptService = new TokenAttemptService();

    @Test
    void staysUnlocked_untilTheFifthFailure() {
        String token = "guess-token-1";

        for (int i = 0; i < 4; i++) {
            assertThat(tokenAttemptService.recordFailure(token)).isFalse();
            assertThat(tokenAttemptService.isLocked(token)).isFalse();
        }

        assertThat(tokenAttemptService.recordFailure(token)).isTrue();
        assertThat(tokenAttemptService.isLocked(token)).isTrue();
    }

    @Test
    void successResetsTheFailureCounter() {
        String token = "guess-token-2";

        tokenAttemptService.recordFailure(token);
        tokenAttemptService.recordFailure(token);
        tokenAttemptService.recordSuccess(token);

        for (int i = 0; i < 4; i++) {
            assertThat(tokenAttemptService.recordFailure(token)).isFalse();
        }
        assertThat(tokenAttemptService.isLocked(token)).isFalse();
    }

    @Test
    void differentTokensAreTrackedIndependently() {
        String guessedToken = "attacker-guess";
        String realToken = "real-owners-token";

        for (int i = 0; i < 5; i++) {
            tokenAttemptService.recordFailure(guessedToken);
        }

        assertThat(tokenAttemptService.isLocked(guessedToken)).isTrue();
        // 공격자가 다른 토큰 값을 반복 실패시켜도, 실제 소유자의 토큰은 전혀 영향받지 않는다
        assertThat(tokenAttemptService.isLocked(realToken)).isFalse();
    }
}
