package com.mamoki.ieojuda.global.email.outbox;

public enum EmailOutboxStatus {
    PENDING, // 다음 워커 주기에 처리 대상 (재시도 가드 - 이 상태인 행만 조회된다)
    SENT,    // 발송 성공, 종결
    DEAD     // 최대 시도 횟수 초과, 종결(관리자 개입 필요)
}
