package com.mamoki.ieojuda.global.idempotency.repository;

import com.mamoki.ieojuda.global.idempotency.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {
}
