package com.mamoki.ieojuda.domain.account.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// issue #55 회귀 테스트 - 로그인 실패 5회까지는 잠기지 않다가 5번째 실패에서 잠기고,
// 성공하면 카운터가 초기화됨을 검증한다.
class LoginAttemptServiceTest {

    private final LoginAttemptService loginAttemptService = new LoginAttemptService();

    @Test
    void staysUnlocked_untilTheFifthFailure() {
        String key = "user1@test.com";

        for (int i = 0; i < 4; i++) {
            boolean lockedNow = loginAttemptService.recordFailure(key);
            assertThat(lockedNow).isFalse();
            assertThat(loginAttemptService.isLocked(key)).isFalse();
        }

        boolean lockedOnFifth = loginAttemptService.recordFailure(key);
        assertThat(lockedOnFifth).isTrue();
        assertThat(loginAttemptService.isLocked(key)).isTrue();
    }

    @Test
    void successResetsTheFailureCounter() {
        String key = "user2@test.com";

        loginAttemptService.recordFailure(key);
        loginAttemptService.recordFailure(key);
        loginAttemptService.recordSuccess(key);

        // 초기화됐으므로 다시 4번 실패해도(총 6번째지만 카운터는 리셋됨) 아직 잠기면 안 된다
        for (int i = 0; i < 4; i++) {
            assertThat(loginAttemptService.recordFailure(key)).isFalse();
        }
        assertThat(loginAttemptService.isLocked(key)).isFalse();
    }

    @Test
    void differentAccountsAreTrackedIndependently() {
        String keyA = "victim@test.com";
        String keyB = "unrelated@test.com";

        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure(keyA);
        }
        assertThat(loginAttemptService.isLocked(keyA)).isTrue();
        assertThat(loginAttemptService.isLocked(keyB)).isFalse();
    }

    @Test
    void neverLocked_reportsUnlocked() {
        assertThat(loginAttemptService.isLocked("never-tried@test.com")).isFalse();
    }
}
