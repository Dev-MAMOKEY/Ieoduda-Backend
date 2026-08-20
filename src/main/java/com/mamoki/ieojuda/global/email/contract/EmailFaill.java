package com.mamoki.ieojuda.global.email.contract;

public enum EmailFaill {
    INVALID_ADDRESS_FORMAT,   // 주소 형식 오류 (SendFailedException, 555 5.5.2)
    AUTHENTICATION_FAILED,    // SMTP 인증 실패 (MailAuthenticationException)
    CONNECTION_TIMEOUT,       // 연결 타임아웃/서버 응답 없음
    UNKNOWN                   // 실패 원인 분류 할 수 없음 (위에 케이스 이외 예외)
}
