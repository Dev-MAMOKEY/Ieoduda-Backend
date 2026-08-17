package com.mamoki.ieojuda.domain.postaccess.service;

import com.mamoki.ieojuda.domain.postaccess.repository.AccessTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// OTP 검증 실패 횟수는 PosthumousAccessService.verify()가 CustomException을 던지며 그 트랜잭션을
// 롤백시키기 직전에 기록해야 한다. 같은 트랜잭션에서 늘리면 롤백과 함께 증가분도 사라져 5회 실패
// 차단이 무력화되므로(ReleaseCaseGuardService.freeze()와 동일한 문제), 별도 빈으로 분리해
// REQUIRES_NEW로 독립 커밋한다.
@Service
@RequiredArgsConstructor
public class OtpAttemptRecorder {

    private final AccessTokenRepository accessTokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long accessTokenId) {
        accessTokenRepository.findById(accessTokenId).ifPresent(token -> token.incrementAttempt());
    }
}
