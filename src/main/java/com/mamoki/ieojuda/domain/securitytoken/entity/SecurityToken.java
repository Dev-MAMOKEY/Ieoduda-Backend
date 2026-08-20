package com.mamoki.ieojuda.domain.securitytoken.entity;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.entity.DisputeContact;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

// issue #41 - 목적별 보안 토큰. 원문 토큰은 저장하지 않고 해시만 저장한다(tokenHash).
// 대상은 confirmer/recipient/disputeContact 중 정확히 하나만 채워진다("대상 바인딩").
// releaseCase는 REPORT_DEATH/UPLOAD_EVIDENCE/RAISE_OBJECTION처럼 특정 사건에 묶이는
// 목적에서만 채워지고, ACCEPT_ROLE은 아직 사건이 없으므로 null이다("사건 바인딩").
@Entity
@Table(name = "security_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecurityToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "token_id")
    private UUID tokenId;

    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", length = 30, nullable = false)
    private SecurityTokenPurpose purpose;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmer_id")
    private Confirmer confirmer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    private Recipient recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispute_contact_id")
    private DisputeContact disputeContact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    private ReleaseCase releaseCase;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public SecurityToken(String tokenHash, SecurityTokenPurpose purpose, Confirmer confirmer, Recipient recipient,
                          DisputeContact disputeContact, ReleaseCase releaseCase, LocalDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.purpose = purpose;
        this.confirmer = confirmer;
        this.recipient = recipient;
        this.disputeContact = disputeContact;
        this.releaseCase = releaseCase;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    // 재발급·연락처변경·사건종료 시 기존 토큰 폐기
    public void revoke() {
        this.revokedAt = LocalDateTime.now();
    }
}
