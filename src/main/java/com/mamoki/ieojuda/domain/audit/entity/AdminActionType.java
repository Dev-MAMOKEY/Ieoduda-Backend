package com.mamoki.ieojuda.domain.audit.entity;

// issue #59 - 감사 대상 고위험 관리자/파트너 조작
public enum AdminActionType {
    CASE_FREEZE,          // 사건 동결
    CASE_ASSIGN_PARTNER,  // 사건에 파트너사 배정
    EVIDENCE_DECISION     // 증빙 승인/반려/추가자료요청
}
