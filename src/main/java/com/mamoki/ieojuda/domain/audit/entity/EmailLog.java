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

    @Column(name = "message_id", length = 255)
    private String messageId;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "bounced_at")
    private LocalDateTime bouncedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Builder
    public EmailLog(Plan plan, HandoverStage handoverStage, EmailType emailType,
                    String recipientEmail, String messageId) {
        this.plan = plan;
        this.handoverStage = handoverStage;
        this.emailType = emailType;
        this.recipientEmail = recipientEmail;
        this.messageId = messageId;
        this.retryCount = 0;
        this.sentAt = LocalDateTime.now();
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

    // "이메일 발송 감사" 화면 "재시도 정책 실행하기" - 반송/실패 건 재발송 시 호출
    public void markRetried(String newMessageId) {
        this.retryCount = this.retryCount + 1;
        this.messageId = newMessageId;
        this.sentAt = LocalDateTime.now();
        this.bouncedAt = null;
    }
}
