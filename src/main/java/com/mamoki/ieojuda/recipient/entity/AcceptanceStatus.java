package com.mamoki.ieojuda.recipient.entity;

public enum AcceptanceStatus {
    PENDING,   // 대기 중 (초대 발송, 응답 대기)
    ACCEPTED,  // 수락
    DECLINED,  // 거절
    EXPIRED    // 만료 (링크 유효기간 초과)
}
