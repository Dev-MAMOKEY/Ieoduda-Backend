package com.mamoki.ieojuda.domain.stage.service;

// issue #79 - 사후 인계 이메일이 최초 발송인지 대체 담당자 전환인지에 따라 문구가 달라야 한다.
public enum InviteKind {
    INITIAL,  // 이 담당자의 순서가 정상적으로 돌아옴 (최초 발송 / 다음 단계로 진행)
    FALLBACK  // 이전 담당자가 응답하지 않아 대체 담당자로 전환됨
}
