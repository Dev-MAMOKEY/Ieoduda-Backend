package com.mamoki.ieojuda.global.rsdata;

import com.mamoki.ieojuda.global.exception.ErrorCode;

import java.time.LocalDateTime;

public record RsData<T>( // 공통 API 응답 객체
                         boolean success,
                         T data,
                         ErrorInfo error,
                         LocalDateTime timestamp
){
    public record ErrorInfo( // 에러코드에 들어갈 객체
                             String code,
                             String message
    ) {
    }

    // 성공 응답 (200)
    public static <T> RsData<T> success(T data) {
        return new RsData<>(true, data, null, LocalDateTime.now());
    }

    // 실패 응답
    public static <T> RsData<T> fail(ErrorCode errorCode) {
        return new RsData<>(false, null, new ErrorInfo(errorCode.getCode(), errorCode.getMessage()), LocalDateTime.now());
    }

    // 실패 응답 (검증 실패처럼 상세 메시지를 직접 지정해야 할 때 사용)
    public static <T> RsData<T> fail(ErrorCode errorCode, String message) {
        return new RsData<>(false, null, new ErrorInfo(errorCode.getCode(), message), LocalDateTime.now());
    }
}