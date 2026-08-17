package com.mamoki.ieojuda.global.idempotency.service;

import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.idempotency.entity.IdempotencyKey;
import com.mamoki.ieojuda.global.idempotency.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

// 클라이언트가 Idempotency-Key 헤더를 보낸 요청을 (scope, key) 조합으로 선점한다.
// 호출부의 트랜잭션에 그대로 참여한다 - 이후 비즈니스 로직이 실패해 트랜잭션이 롤백되면 이 선점도
// 함께 롤백되어 재시도가 가능하고, 성공적으로 커밋된 요청만 진짜로 키를 소비한 것이 된다.
@Component
@RequiredArgsConstructor
public class IdempotencyGuard {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    // idempotencyKey가 없으면(헤더 미전송) 아무 것도 하지 않는다 - 이 가드는 옵트인이다.
    public void claim(String scope, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        try {
            idempotencyKeyRepository.saveAndFlush(
                    IdempotencyKey.builder().scope(scope).keyValue(idempotencyKey).build());
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.DUPLICATE_REQUEST);
        }
    }
}
