package com.mamoki.ieojuda.global.idempotency.repository;

import com.mamoki.ieojuda.global.idempotency.entity.IdempotencyKey;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {
}
