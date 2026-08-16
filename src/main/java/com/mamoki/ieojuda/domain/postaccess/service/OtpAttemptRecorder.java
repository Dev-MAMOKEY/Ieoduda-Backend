package com.mamoki.ieojuda.domain.postaccess.service;

import com.mamoki.ieojuda.domain.postaccess.entity.AccessToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// OTP 검증 실패 시 시도 횟수를 별도 트랜잭션으로 즉시 커밋한다.
// PosthumousAccessService.verifyOtp()가 실패 시 예외를 던지면 그 트랜잭션 안의 변경은 전부 롤백되므로,
// increaseAttempt()가 함께 사라져 시도 제한이 걸리지 않는다 - 그래서 REQUIRES_NEW로 분리한다
@Component
public class OtpAttemptRecorder {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedAttempt(AccessToken token) {
        token.increaseAttempt();
    }
}
