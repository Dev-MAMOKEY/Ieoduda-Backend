package com.mamoki.ieojuda.audit.entity;

public enum EmailType {
    ROLE_ACCEPTANCE_INVITE,       // 역할 수락 요청
    CONFIRMER_ACCEPTANCE_INVITE,  // 확인자 수락 요청
    DISPUTE_CONTACT_VERIFICATION, // 이의 제기 연락처 검증
    DEATH_REPORT_REQUEST,         // 사망 신고 안내
    EVIDENCE_SUBMISSION_REQUEST,  // 증빙 제출 안내
    WAITING_PERIOD_WARNING,       // 대기 기간 경고
    POSTHUMOUS_HANDOFF_LINK,      // 사후 인계 링크
    OTP,                          // OTP 코드
    HANDOFF_CHECK
}
