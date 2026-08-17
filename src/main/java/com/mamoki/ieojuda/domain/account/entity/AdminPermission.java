package com.mamoki.ieojuda.domain.account.entity;

// issue #59 - ROLE_ADMIN/ROLE_EXTERNAL 하나에 뭉쳐 있던 권한을 업무 단위로 세분화.
// role 승격과 마찬가지로 DB에서 직접 부여한다(별도 부여 API 없음).
public enum AdminPermission {
    CASE_SUPERVISE,        // 사건 동결, 재시도 발송, 대체 담당자 전환, 파트너사 배정
    EVIDENCE_REVIEW,       // 외부 파트너 증빙 검토(승인/반려/추가자료요청)
    AUDIT_VIEW,            // 이메일 발송 감사, 인증 실패 감사, 관리자 조작 감사 조회
    EVIDENCE_DELETE_RETRY  // 증빙 삭제 상태 조회 및 재처리
}
