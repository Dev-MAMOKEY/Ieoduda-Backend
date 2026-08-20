package com.mamoki.ieojuda.domain.plan.entity;

// issue #90 - 명세서 "AI 구조화 및 충돌 정책"의 결정론적 규칙 중 순서 쌍으로 검사하는 3가지를
// 판정하기 위한 세부 분류. actionType(DELETE/TRANSFER/OTHER)보다 한 단계 더 구체적이며,
// 이 6가지 중 어디에도 해당하지 않는 항목은 null로 둔다(모든 항목이 이 분류를 가질 필요는 없음).
public enum ItemSemanticType {
    CLOUD_DELETE,   // 클라우드/계정 삭제 - FILE_PRESERVE보다 먼저 오면 충돌
    FILE_PRESERVE,  // 사진·업무 파일 보존(인계)
    CHANNEL_CLOSE,  // 거래처 연락 채널 폐쇄 - CLIENT_NOTIFY보다 먼저 오면 충돌
    CLIENT_NOTIFY,  // 거래처 통지
    DEVICE_RESET,   // 기기 초기화 - RECORD_EXPORT보다 먼저 오면 충돌
    RECORD_EXPORT   // 기록 반출
}
