package com.mamoki.ieojuda.domain.audit.entity;

public enum AuthAuditEventType {
    LOGIN_FAILURE,               // 비밀번호 불일치 등 일반 로그인 실패
    LOGIN_LOCKED,                // 이번 실패로 계정이 잠금 상태로 전환됨
    LOGIN_LOCKED_ATTEMPT,        // 이미 잠긴 계정에 재시도
    RATE_LIMIT_EXCEEDED,         // 경로별 rate limit 초과
    PUBLIC_LINK_TOKEN_FAILURE,   // 사망신고/증빙/이의제기/역할수락 등 공개 링크에서 존재하지 않는 토큰 시도
    PUBLIC_LINK_TOKEN_LOCKED,    // 이번 실패로 해당 토큰이 잠금 상태로 전환됨
    PUBLIC_LINK_TOKEN_LOCKED_ATTEMPT, // 이미 잠긴 토큰에 재시도
    PUBLIC_LINK_STATE_FAILURE   // 토큰 자체는 유효하지만 만료/재사용/미검증 등 상태가 안 맞아 실패 (detail에 구체 사유 기록)
}
