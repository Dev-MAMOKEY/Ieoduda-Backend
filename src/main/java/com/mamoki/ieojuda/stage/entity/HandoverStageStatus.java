package com.mamoki.ieojuda.stage.entity;

public enum HandoverStageStatus {
    PENDING,   // 대기 (선행 단계 미완료)
    READY,     // 발송 준비 완료
    SENT,      // 발송됨
    COMPLETED, // 완료
    BOUNCED,   // 반송
    FALLBACK,  // 대체 담당자 전환됨
    BLOCKED    // 차단 (대체 담당자 없음)
}
