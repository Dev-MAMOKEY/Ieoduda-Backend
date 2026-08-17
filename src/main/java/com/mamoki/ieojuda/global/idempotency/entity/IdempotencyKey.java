package com.mamoki.ieojuda.global.idempotency.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.time.LocalDateTime;

// 클라이언트가 보낸 Idempotency-Key를 (요청 종류, 키 값) 조합으로 선점하는 마커.
// 같은 조합으로 두 번째 INSERT가 들어오면 DB 유니크 제약이 즉시 막아준다 - 그 신호로 중복 요청을 판별한다.
@Entity
@Table(name = "idempotency_keys", uniqueConstraints = @UniqueConstraint(columnNames = {"scope", "key_value"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "idempotency_key_id")
    private UUID idempotencyKeyId;

    @Column(name = "scope", length = 100, nullable = false)
    private String scope;

    @Column(name = "key_value", length = 255, nullable = false)
    private String keyValue;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public IdempotencyKey(String scope, String keyValue) {
        this.scope = scope;
        this.keyValue = keyValue;
        this.createdAt = LocalDateTime.now();
    }
}
