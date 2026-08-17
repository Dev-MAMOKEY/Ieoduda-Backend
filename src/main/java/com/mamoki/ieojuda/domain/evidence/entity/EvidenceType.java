package com.mamoki.ieojuda.domain.evidence.entity;

// issue #88 - 인정 증빙 종류의 최종 확정은 "외부 파트너와 법률 검토 후" 결정되므로, 이후에도 값 추가만으로
// 확장 가능하도록 열어둔다.
public enum EvidenceType {
    DEATH_CERTIFICATE,   // 사망 진단서
    DEATH_REPORT,        // 사망 신고서
    POSTMORTEM_REPORT    // 검안서
}
