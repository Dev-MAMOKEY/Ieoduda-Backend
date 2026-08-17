package com.mamoki.ieojuda.domain.audit.entity;

// issue #59 - 감사 대상 고위험 관리자/파트너 조작
public enum AdminActionType {
    CASE_FREEZE,          // 사건 동결
    CASE_UNFREEZE,        // 사건 동결 해제
    CASE_WARNING_RETRY,   // 취소·이의 제기 경고 발송 재시도
    EVIDENCE_DECISION,    // 증빙 승인/반려/추가자료요청
    EVIDENCE_DELETE,      // 증빙 원본 삭제(수동 재처리 또는 자동 스케줄러)
    EVIDENCE_DOWNLOAD,    // issue #43 - 증빙 원본 다운로드(1회성 링크 발급 및 소비)
    EMAIL_OUTBOX_DISPATCH_FAILED, // issue #51 - 이메일 아웃박스 최대 재시도 초과(DEAD 전이)
    EVIDENCE_ORPHAN_CLEANUP // issue #51 - 트랜잭션 롤백 후 S3 고아 객체 정리 실패
}
