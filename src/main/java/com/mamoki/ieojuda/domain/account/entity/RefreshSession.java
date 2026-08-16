package com.mamoki.ieojuda.domain.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// issue #56 - Refresh Token 1건 = 세션 1건. 로그인 1회가 "가족(family)"을 이루고, 재발급(회전)마다
// 같은 family 안에서 새 세션이 이어진다. 이미 회전되어 사용된(usedAt != null) 세션이 다시 제시되면
// 탈취로 간주해 family 전체를 차단한다(재사용 탐지). sessionId 자체가 Refresh Token의 jti가 된다.
@Entity
@Table(name = "refresh_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshSession {

    @Id
    @Column(name = "session_id", length = 36)
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 이 세션 lineage(로그인 1회~로그아웃/만료까지 이어지는 회전 사슬) 전체를 묶는 ID.
    // 재사용 탐지 시 이 값으로 관련 세션을 전부 찾아서 차단한다.
    @Column(name = "family_id", length = 36, nullable = false)
    private String familyId;

    // Refresh Token 원문은 저장하지 않고 SHA-256 해시만 저장 - DB가 유출돼도 원문을 복원할 수 없다
    @Column(name = "token_hash", length = 64, nullable = false)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // 회전(재발급)에 사용된 시각 - null이 아니면 이미 소모된 세션. 이 상태에서 다시 제시되면 재사용 탐지.
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    // 로그아웃/재사용탐지/보안 이벤트로 명시적으로 차단된 시각
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "replaced_by_session_id", length = 36)
    private String replacedBySessionId;

    @Builder
    public RefreshSession(String sessionId, User user, String familyId, String tokenHash,
                           LocalDateTime issuedAt, LocalDateTime expiresAt) {
        this.sessionId = sessionId;
        this.user = user;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public boolean isActive() {
        return revokedAt == null && usedAt == null && expiresAt.isAfter(LocalDateTime.now());
    }

    // 회전(재발급)으로 소모됨 - 다음 세션으로 이어짐을 함께 기록
    public void markUsed(String replacedBySessionId) {
        this.usedAt = LocalDateTime.now();
        this.replacedBySessionId = replacedBySessionId;
    }

    public void revoke() {
        this.revokedAt = LocalDateTime.now();
    }
}
