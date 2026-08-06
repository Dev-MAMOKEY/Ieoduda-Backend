package com.mamoki.ieojuda.global.email.contract;

public record EmailSendResult(
        boolean success,
        String messageId,      // 우리 서버가 발급한 메일 한통을 가르키는 고유 식별자
        BounceType bounceType,// 실패 원인
        EmailFaill emailFaill
) {
    public static EmailSendResult success(String messageId) {
        return new EmailSendResult(true, messageId, BounceType.NONE, null);
    }

    public static EmailSendResult failure(BounceType bounceType, EmailFaill failureReason) {
        return new EmailSendResult(false, null, bounceType, failureReason);
    }
}
