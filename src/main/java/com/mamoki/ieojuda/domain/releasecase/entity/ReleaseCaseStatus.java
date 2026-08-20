package com.mamoki.ieojuda.domain.releasecase.entity;

public enum ReleaseCaseStatus {
    REPORT_PENDING,     // 신고 대기
    REPORT_CONFIRMED,   // 신고 확인됨 (2인 일치)
    EVIDENCE_PENDING,   // 증빙 제출 대기
    EVIDENCE_REVIEWING, // 증빙 검토 중
    EVIDENCE_APPROVED,  // 증빙 승인됨
    EVIDENCE_REJECTED,  // 증빙 반려됨
    WAITING,            // 대기 기간 진행 중
    DISPUTED,           // 이의 제기됨
    CANCELED,           // 취소됨
    RELEASING,          // 발송 진행 중
    COMPLETED           // 완료됨
}
