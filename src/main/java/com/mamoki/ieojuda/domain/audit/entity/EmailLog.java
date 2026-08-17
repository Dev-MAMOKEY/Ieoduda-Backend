package com.mamoki.ieojuda.domain.audit.entity;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = true)
    private HandoverStage handoverStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_type", length = 30)
    private EmailType emailType;

    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private EmailDeliveryStatus status;

    @Column(name = "message_id", length = 255)
    private String messageId;

    @Column(name = "retry_count")
    private Integer retryCount;

    // 이 로그가 생성된(=아웃박스에 등록된) 시각. sentAt과 달리 항상 채워진다.
    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    // 실제로 SMTP 발송이 성공한 시각. markSent() 호출 전까지는 null.
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "bounced_at")
    private LocalDateTime bouncedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Builder
    public EmailLog(Plan plan, HandoverStage handoverStage, EmailType emailType, String recipientEmail) {
        this.plan = plan;
        this.handoverStage = handoverStage;
        this.emailType = emailType;
        this.recipientEmail = recipientEmail;
        this.retryCount = 0;
        this.status = EmailDeliveryStatus.REQUESTED;
        this.requestedAt = LocalDateTime.now();
    }

    // 워커가 실제 SMTP 발송에 성공했을 때만 호출 - 이 메서드가 유일한 SENT/sentAt 기록 경로다.
    public void markSent(String messageId) {
        this.status = EmailDeliveryStatus.SENT;
        this.messageId = messageId;
        this.sentAt = LocalDateTime.now();
        this.failureReason = null;
    }

    // 워커의 이번 시도가 실패했을 때 호출 (재시도 대상 여부와 무관하게 매 실패마다 기록)
    public void markFailed(String reason) {
        this.status = EmailDeliveryStatus.FAILED;
        this.failureReason = reason;
    }

    public void markOpened() {
        this.openedAt = LocalDateTime.now();
    }

    public void markBounced() {
        this.bouncedAt = LocalDateTime.now();
    }

    public void markCanceled() {
        this.canceledAt = LocalDateTime.now();
    }

    // "이메일 발송 감사" 화면 "재시도 정책 실행하기" - 관리자가 재시도를 요청한 시점에 호출.
    // 실제 재발송 성공 여부는 이후 워커가 markSent/markFailed로 비동기 기록한다.
    public void recordRetryRequested() {
        this.retryCount = this.retryCount + 1;
        this.status = EmailDeliveryStatus.REQUESTED;
        this.bouncedAt = null;
        this.failureReason = null;
    }
}
