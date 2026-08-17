package com.mamoki.ieojuda.domain.evidence.entity;

import com.mamoki.ieojuda.domain.partner.entity.PartnerReviewer;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

// issue #43 - "짧은 수명의 1회성 다운로드 URL". 배정된 검토자가 원본을 열람하려면 먼저 이 토큰을
// 발급받아야 하고, 실제 다운로드는 이 토큰을 한 번 소비해야 성립한다 - 로그인 세션(JWT)만으로
// 원본에 계속 접근할 수 있는 상태를 막기 위한 별도 계층이다. 원문 토큰은 저장하지 않고 해시만 저장한다.
@Entity
@Table(name = "evidence_download_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvidenceDownloadToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "token_id")
    private UUID tokenId;

    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_id", nullable = false)
    private Evidence evidence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private PartnerReviewer reviewer;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public EvidenceDownloadToken(String tokenHash, Evidence evidence, PartnerReviewer reviewer, LocalDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.evidence = evidence;
        this.reviewer = reviewer;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }
}
