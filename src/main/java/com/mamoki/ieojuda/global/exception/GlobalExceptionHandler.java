package com.mamoki.ieojuda.global.exception;

import com.mamoki.ieojuda.global.rsdata.RsData;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice // 컨트롤러에 대해 전역적으로 예외 처리하기 위해 사용하는 어노테이션
public class GlobalExceptionHandler {

    // ErrorCode 순서 기준으로 예외 순서 정리함

    // 요청 값 검증 실패 (400) -> @Valid 검증 실패 시 첫 번째 필드의 에러 메시지를 응답에 담아 반환
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RsData<Void>> handle(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.INVALID_INPUT.getMessage());
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getHttpStatus())
                .body(RsData.fail(ErrorCode.INVALID_INPUT, message));
    }

    // 요청 본문 파싱 실패 (400) -> JSON 형식이 잘못됐거나 필드 타입이 맞지 않는 경우
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RsData<Void>> handle(HttpMessageNotReadableException exception) {
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getHttpStatus())
                .body(RsData.fail(ErrorCode.INVALID_INPUT, "요청 본문(JSON) 형식이 올바르지 않습니다."));
    }

    // 요청 파라미터 타입 불일치 (400) -> 쿼리/경로 파라미터가 enum이나 숫자로 변환되지 않는 경우
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<RsData<Void>> handle(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getHttpStatus())
                .body(RsData.fail(ErrorCode.INVALID_INPUT, "요청 파라미터 '" + exception.getName() + "' 값이 올바르지 않습니다."));
    }

    // multipart 파일 또는 요청 전체 크기 초과 (413)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<RsData<Void>> handle(MaxUploadSizeExceededException exception) {
        return ResponseEntity
                .status(ErrorCode.PAYLOAD_TOO_LARGE.getHttpStatus())
                .body(RsData.fail(ErrorCode.PAYLOAD_TOO_LARGE));
    }

    // 인증 관련 예외 처리 (401)
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<RsData<Void>> handleBadCredentials (BadCredentialsException exception) {
        return ResponseEntity
                .status(ErrorCode.INVALID_CREDENTIALS.getHttpStatus())
                .body(RsData.fail(ErrorCode.INVALID_CREDENTIALS));
    }

    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<RsData<Void>> handle(ExpiredJwtException exception) {
        return ResponseEntity
                .status(ErrorCode.TOKEN_EXPIRED.getHttpStatus())
                .body(RsData.fail(ErrorCode.TOKEN_EXPIRED));
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<RsData<Void>> handle(JwtException exception) {
        return ResponseEntity
                .status(ErrorCode.TOKEN_INVALID.getHttpStatus())
                .body(RsData.fail(ErrorCode.TOKEN_INVALID));
    }

    // 권한 관련 예외 처리 (403)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<RsData<Void>> handle(AccessDeniedException exception) {
        return ResponseEntity
                .status(ErrorCode.FORBIDDEN.getHttpStatus())
                .body(RsData.fail(ErrorCode.FORBIDDEN));
    }

    // 존재하지 않는 경우 예외 처리 (404) -> JPA 조회 실패 등 특정 도메인에 종속되지 않는 일반적인 리소스 없음 처리
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<RsData<Void>> handle(EntityNotFoundException exception) {
        return ResponseEntity
                .status(ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus())
                .body(RsData.fail(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // 낙관적 잠금 충돌 (409) -> @Version이 걸린 엔티티(사건/증빙/단계)를 동시에 수정하려다 한쪽이 밀려난 경우
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<RsData<Void>> handle(OptimisticLockingFailureException exception) {
        return ResponseEntity
                .status(ErrorCode.CONCURRENT_MODIFICATION.getHttpStatus())
                .body(RsData.fail(ErrorCode.CONCURRENT_MODIFICATION));
    }

    // 커스텀 예외 처리 (나머지 예외는 스프링에서 자동으로 적용시켜주는 것이 없어 커스텀으로 처리함)
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<RsData<Void>> handle(CustomException exception) {
        return ResponseEntity
                .status(exception.getErrorCode().getHttpStatus())
                .body(RsData.fail(exception.getErrorCode()));
    }

    // 서버 에러 (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RsData<Void>> handle(Exception exception) {
        log.error("[500 Internal Server Error] {}: {}", exception.getClass().getName(), exception.getMessage(), exception);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(RsData.fail(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
