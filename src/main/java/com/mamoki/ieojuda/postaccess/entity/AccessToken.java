package com.mamoki.ieojuda.postaccess.entity;

import com.mamoki.ieojuda.stage.entity.HandoverStage;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "posthumouse_access_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccessToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long tokenId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private HandoverStage handoverStage;

    @Column(name = "token_hash", length = 255)
    private String tokenHash;

    @Column(name = "otp_code_hash", length = 255)
    private String otpCodeHash;

    @Column(name = "otp_sent_at")
    private LocalDateTime otpSentAt;

    @Column(name = "attempt_count")
    private Integer attemptCount;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "used")
    private Boolean used;

    @Builder
    public AccessToken(HandoverStage handoverStage, String tokenHash, LocalDateTime expiresAt) {
        this.handoverStage = handoverStage;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.attemptCount = 0;
        this.used = false;
    }


}
