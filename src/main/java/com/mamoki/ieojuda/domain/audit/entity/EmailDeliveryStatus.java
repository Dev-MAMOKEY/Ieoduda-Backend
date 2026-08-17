package com.mamoki.ieojuda.domain.audit.entity;

// issue #51 - SMTP 발송을 아웃박스로 분리하면서 도입. DELIVERED/BOUNCED/OPENED는
// 공급자 웹훅 없이는 신뢰할 수 없어 이번 스코프에서 제외하고 기존 bouncedAt/openedAt 컬럼을 그대로 둔다.
public enum EmailDeliveryStatus {
    REQUESTED, // 아웃박스에 등록됨 - 아직 실제 SMTP 시도 전
    SENT,      // JavaMailSender가 예외 없이 반환함 (확인된 성공만 이 상태)
    FAILED     // 이번 시도 실패 (재시도 대상이면 워커가 다음 주기에 다시 REQUESTED로 시도)
}
