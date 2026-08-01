package com.mamoki.ieojuda.confirmer.entity;

public enum ReportStatus {
    NOT_REPORTED, // 미신고
    REPORTED,     // 신고 완료 (상대방 신고 대기)
    MATCHED,      // 신고 일치
    MISMATCHED    // 신고 불일치
}