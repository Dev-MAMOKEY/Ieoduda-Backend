package com.mamoki.ieojuda.global.email.token;

import java.time.Instant;

// 토큰의 만료 여부와 사용가능 여부 판단하는 함수, 예외처리는 이 결과를 사용하는 서비스 레이어 담당

public class TokenValidator {

    private TokenValidator() {
        // 인스턴스화 방지
    }

    /**
     * 토큰이 만료되었는지 판단.
     * @param  expiresAt 토큰의 만료 시각
     * @param  now        기준 시각 (호출부에서 Instant.now()를 넘겨줌)
     * 만료되었으면 true
     */
    public static boolean isExpired(Instant expiresAt, Instant now) {
        if (expiresAt == null || now == null) {
            return true; // 만료 시각이 없으면 안전하게 "만료됨"으로 처리
        }
        return now.isAfter(expiresAt);
    }

    /**
     * 1회성 링크의 사용 가능 여부.
     * 이미 사용된(used=true) 링크는 재사용할 수 없음
     * @param  used 이미 사용되었는지 여부 (DB의 used 컬럼)
     *아직 사용 가능하면 true
     */
    public static boolean isUsable(boolean used) {
        return !used;
    }

    /**
     * 제한 횟수 기반 링크의 사용 가능 여부.(여러번 가능)
     * @param  attemptCount: 지금까지 시도한 횟수
     * @param  maxAttempts :  허용된 최대 시도 횟수
     * 아직 시도 가능하면 true
     */
    public static boolean isUsable(int attemptCount, int maxAttempts) {
        return attemptCount < maxAttempts;
    }

    /**
     * 토큰이 지금 시점에 실제로 사용 가능한 상태인지 종합 판단 (만료 + 1회성).
     * @param expiresAt: 만료 시각
     * @param now:        기준 시각
     * @param used:       이미 사용되었는지 여부
     * 만료되지 않았고 아직 사용 안 됐으면 true
     */
    public static boolean isValid(Instant expiresAt, Instant now, boolean used) {
        return !isExpired(expiresAt, now) && isUsable(used);
    }
}
