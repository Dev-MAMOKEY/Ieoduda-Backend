package com.mamoki.ieojuda.global.email.sender;

import com.mamoki.ieojuda.global.email.contract.BounceType;

import java.time.Instant;

public class RetryPolicy {

    private RetryPolicy() {
        // 순수 정적 유틸 클래스 — 인스턴스화 방지
    }

    /**
     * 일시 반송(TEMPORARY) 상황에서 재시도 가능 여부를 판단.
     * PERMANENT -> 반송X ->  항상 false.
     *
     * @param bounceType   FailureAnalyzer가 분류한 반송 유형
     * @param retryCount   지금까지 재시도한 횟수
     * @param maxRetries   허용된 최대 재시도 횟수
     * @return 재시도 가능하면 true
     */
    public static boolean canRetry(BounceType bounceType, int retryCount, int maxRetries) {
        if (bounceType != BounceType.TEMPORARY) {
            return false;
        }
        return retryCount < maxRetries;
    }

    /**
     * 담당자가 최대 대기 시간을 초과했는지 판단.
     * role_assigness.max_wait_hours(담당자 테이블 -> max_wait_hours 필드 기준.)
     *
     * @param sentAt        이메일이 처음 발송된 시각
     * @param maxWaitHours  허용된 최대 대기 시간(시간 단위)
     * @param now           기준 시각 (호출부에서 Instant.now()를 넘겨줌)
     * @return 최대 대기 시간을 초과했으면 true
     */
    public static boolean isWaitExceeded(Instant sentAt, long maxWaitHours, Instant now) {
        if (sentAt == null || now == null) {
            return true; // 발송 시각이 없으면 안전하게 "초과됨"으로 처리
        }
        Instant deadline = sentAt.plusSeconds(maxWaitHours * 3600);
        return now.isAfter(deadline);
    }

    /**
     * 현재 담당자 처리가 끝난 뒤(반송/대기초과 등), 다음 단계로 자동 진행해도 되는지 판단.
     * 대체 담당자가 없으면 절대 자동 진행하지 않는다 (FALLBACK_RECIPIENT_MISSING 규칙의 실체).
     *
     * @param bounceType   반송 유형 (PERMANENT면 이 담당자는 더 이상 가망 없음)
     * @param hasFallback  사전 수락된 대체 담당자가 있는지 여부
     * @return 다음 단계로 진행 가능하면 true
     */
    public static boolean canProceed(BounceType bounceType, boolean hasFallback) {
        if (bounceType == BounceType.PERMANENT) {
            return hasFallback;
        }
        return true; // TEMPORARY/NONE이면 아직 재시도 여지가 있으므로 이 판단 대상 아님
    }
}