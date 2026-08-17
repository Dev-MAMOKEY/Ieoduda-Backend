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

    // OTP 인증 성공 후 발급되는 열람 세션 식별자(해시만 저장, 평문은 응답으로만 반환)
    @Column(name = "session_token_hash", length = 255)
    private String sessionTokenHash;

    @Column(name = "session_expires_at")
    private LocalDateTime sessionExpiresAt;

    @Builder
    public AccessToken(HandoverStage handoverStage, String tokenHash, LocalDateTime expiresAt) {
        this.handoverStage = handoverStage;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.attemptCount = 0;
        this.used = false;
    }

    // OTP 코드 발송 - 코드 해시와 발송 시각 갱신 (재발송 시에도 동일하게 덮어씀)
    public void recordOtpSend(String otpCodeHash) {
        this.otpCodeHash = otpCodeHash;
        this.otpSentAt = LocalDateTime.now();
    }

    // OTP 검증 실패 1회 기록, 갱신된 시도 횟수를 반환
    public int incrementAttempt() {
        this.attemptCount = this.attemptCount + 1;
        return this.attemptCount;
    }

    // OTP 검증 성공 - 열람 세션을 발급하고 링크 자체는 1회성으로 소진 처리
    public void verify(String sessionTokenHash, LocalDateTime sessionExpiresAt) {
        this.verifiedAt = LocalDateTime.now();
        this.used = true;
        this.sessionTokenHash = sessionTokenHash;
        this.sessionExpiresAt = sessionExpiresAt;
    }
}
