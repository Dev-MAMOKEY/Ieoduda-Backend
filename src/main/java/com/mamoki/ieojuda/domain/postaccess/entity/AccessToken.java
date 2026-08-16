package com.mamoki.ieojuda.domain.postaccess.entity;

import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
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

    // OTP 발송/재발송 - 인코딩된 해시만 저장, 시도 횟수는 재발송마다 초기화(와이어프레임 "코드 재발송")
    public void issueOtp(String otpCodeHash) {
        this.otpCodeHash = otpCodeHash;
        this.otpSentAt = LocalDateTime.now();
        this.attemptCount = 0;
    }

    // OTP 검증 시도 1회 증가
    public void increaseAttempt() {
        this.attemptCount = this.attemptCount + 1;
    }

    // OTP 검증 성공 - 링크를 소진 처리해 재사용을 막는다
    public void verify() {
        this.verifiedAt = LocalDateTime.now();
        this.used = true;
    }
}
