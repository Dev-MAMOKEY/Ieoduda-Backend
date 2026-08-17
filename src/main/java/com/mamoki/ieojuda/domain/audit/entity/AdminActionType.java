package com.mamoki.ieojuda.domain.audit.entity;

// issue #59 - 감사 대상 고위험 관리자/파트너 조작
public enum AdminActionType {
    CASE_FREEZE,          // 사건 동결
    EVIDENCE_DECISION,    // 증빙 승인/반려/추가자료요청
    EVIDENCE_DELETE,      // 증빙 원본 삭제(수동 재처리 또는 자동 스케줄러)
    EMAIL_OUTBOX_DISPATCH_FAILED, // issue #51 - 이메일 아웃박스 최대 재시도 초과(DEAD 전이)
    EVIDENCE_ORPHAN_CLEANUP // issue #51 - 트랜잭션 롤백 후 S3 고아 객체 정리 실패
}
