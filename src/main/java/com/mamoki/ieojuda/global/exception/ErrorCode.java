package com.mamoki.ieojuda.global.exception;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // 요청 값 검증 예외 (400) -> @Valid 검증 실패, JSON 파싱 실패, 파라미터 타입 불일치 등
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "입력값이 올바르지 않습니다."),

    // 계획 / 삶의 구역 / AI 구조화 관련 예외 (400)
    INVALID_WAITING_PERIOD(HttpStatus.BAD_REQUEST, "INVALID_WAITING_PERIOD", "대기 기간은 7~30일 사이여야 합니다."),
    CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED", "사후 인계 조건과 안전 범위를 확인해 주세요."),
    SUSPECTED_CREDENTIAL_INPUT(HttpStatus.BAD_REQUEST, "SUSPECTED_CREDENTIAL_INPUT", "비밀번호 등 자격증명으로 의심되는 내용은 저장할 수 없습니다."),
    UNGROUNDED_ITEM_NOT_APPROVABLE(HttpStatus.BAD_REQUEST, "UNGROUNDED_ITEM_NOT_APPROVABLE", "원문 근거가 없는 항목은 승인할 수 없습니다."),
    PROHIBITED_ACTION_DETECTED(HttpStatus.BAD_REQUEST, "PROHIBITED_ACTION_DETECTED", "AI가 처리할 수 없는 금지 행동이 포함되어 있습니다."),
    PACKAGE_SEAL_BLOCKED(HttpStatus.BAD_REQUEST, "PACKAGE_SEAL_BLOCKED", "금지 정보나 근거 없는 항목이 있어 패키지를 봉인할 수 없습니다."),
    CIRCULAR_DEPENDENCY_DETECTED(HttpStatus.BAD_REQUEST, "CIRCULAR_DEPENDENCY_DETECTED", "발송 단계에 순환 의존 관계가 존재합니다."),

    // 담당자 / 확인자 / 이의 제기 연락처 관련 예외 (400)
    INSUFFICIENT_CONFIRMERS(HttpStatus.BAD_REQUEST, "INSUFFICIENT_CONFIRMERS", "수락을 완료한 지정 확인자가 2명 미만이라 계획을 봉인할 수 없습니다."),
    RECIPIENT_NOT_ACCEPTED(HttpStatus.BAD_REQUEST, "RECIPIENT_NOT_ACCEPTED", "역할을 수락하지 않은 담당자는 발송 단계에 포함할 수 없습니다."),
    FALLBACK_RECIPIENT_MISSING(HttpStatus.BAD_REQUEST, "FALLBACK_RECIPIENT_MISSING", "대체 담당자가 없어 다음 단계를 진행할 수 없습니다."),
    DISPUTE_CONTACT_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "DISPUTE_CONTACT_NOT_VERIFIED", "이메일 인증이 완료되지 않은 이의 제기 연락처는 활성화할 수 없습니다."),

    // 사망 신고 / 증빙 관련 예외 (400)
    DEATH_REPORT_MISMATCH(HttpStatus.BAD_REQUEST, "DEATH_REPORT_MISMATCH", "두 확인자의 신고 내용이 일치하지 않아 절차를 중지합니다."),
    EVIDENCE_SUBMISSION_INVALID(HttpStatus.BAD_REQUEST, "EVIDENCE_SUBMISSION_INVALID", "증빙 파일 형식이 올바르지 않거나 보안 검사에 실패했습니다."),

    // 공통 이메일 발송 모듈 관련 예외 (500)
    EMAIL_CONTENT_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "EMAIL_CONTENT_GENERATION_FAILED", "이메일 본문 생성에 실패했습니다."),
    //이메일 토큰 관련 예외 (400/500)
    INVALID_TOKEN_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_TOKEN_FORMAT", "토큰 값이 비어있거나 형식이 올바르지 않습니다."),
    TOKEN_HASHING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "TOKEN_HASHING_FAILED", "토큰 처리 중 서버 오류가 발생했습니다."),

    // 인증 관련 예외 처리 (401)
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "Access Token이 만료되었습니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "토큰 형식이 잘못되었거나 서명이 유효하지 않습니다."),
    REFRESH_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REVOKED", "이미 무효화된 Refresh Token입니다."),
    ACCESS_LINK_EXPIRED(HttpStatus.UNAUTHORIZED, "ACCESS_LINK_EXPIRED", "링크가 만료되었습니다."),
    ACCESS_LINK_ALREADY_USED(HttpStatus.UNAUTHORIZED, "ACCESS_LINK_ALREADY_USED", "이미 사용되었거나 재사용할 수 없는 링크입니다."),
    OTP_VERIFICATION_FAILED(HttpStatus.UNAUTHORIZED, "OTP_VERIFICATION_FAILED", "OTP 인증에 실패했습니다."),

    // 권한 에러 (403)
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "해당 리소스에 대한 권한이 없습니다."),
    ROLE_PACKAGE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ROLE_PACKAGE_ACCESS_DENIED", "본인의 역할 패키지가 아니거나 권한이 없는 요청입니다."),
    REVIEWER_CONFLICT_OF_INTEREST(HttpStatus.FORBIDDEN, "REVIEWER_CONFLICT_OF_INTEREST", "이해충돌 또는 권한 만료로 다른 검토자에게 재배정이 필요합니다."),

    // 리소스 없음 (404)
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "요청한 리소스가 존재하지 않습니다."),
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "해당 계획이 존재하지 않습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "해당 사용자가 존재하지 않습니다."),
    LIFE_AREA_NOT_FOUND(HttpStatus.NOT_FOUND, "LIFE_AREA_NOT_FOUND", "해당 삶의 구역이 존재하지 않습니다."),
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "해당 항목이 존재하지 않습니다."),
    CONFIRMER_NOT_FOUND(HttpStatus.NOT_FOUND, "CONFIRMER_NOT_FOUND", "해당 지정 확인자가 존재하지 않습니다."),
    RECIPIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "RECIPIENT_NOT_FOUND", "해당 역할 담당자가 존재하지 않습니다."),
    RELEASE_CASE_NOT_FOUND(HttpStatus.NOT_FOUND, "RELEASE_CASE_NOT_FOUND", "해당 사후 인계 사건이 존재하지 않습니다."),
    EVIDENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "해당 증빙이 존재하지 않습니다."),

    // 상태 충돌 (409)
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다."),
    DISPUTE_RAISED(HttpStatus.CONFLICT, "DISPUTE_RAISED", "이의 제기가 접수되어 자동 발송이 중지되었습니다."),
    RELEASE_CASE_FROZEN(HttpStatus.CONFLICT, "RELEASE_CASE_FROZEN", "사건이 동결되어 있어 처리할 수 없습니다."),
    ACTIVE_RELEASE_CASE_EXISTS(HttpStatus.CONFLICT, "ACTIVE_RELEASE_CASE_EXISTS", "진행 중인 사후 인계 사건이 있어 계정을 삭제할 수 없습니다."),

    // 서버 에러 (500)
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR","서버 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
