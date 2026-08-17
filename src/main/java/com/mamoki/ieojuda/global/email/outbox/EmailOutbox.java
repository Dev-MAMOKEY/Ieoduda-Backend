package com.mamoki.ieojuda.global.email.outbox;

import com.mamoki.ieojuda.domain.audit.entity.EmailLog;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.time.LocalDateTime;

// issue #51 - SMTP 호출을 DB 트랜잭션 밖으로 빼기 위한 아웃박스 작업 단위.
// 호출자 트랜잭션은 이 행을 EmailLog와 함께 커밋하기만 하고, 실제 발송은 EmailOutboxScheduler가 담당한다.
// 하나의 EmailLog가 여러 EmailOutbox 행을 가질 수 있다(최초 발송 + 관리자 재시도마다 새 행).
@Entity
@Table(name = "email_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "outbox_id")
    private UUID outboxId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "log_id", nullable = false)
    private EmailLog emailLog;

    // 성공 시에만 stage.send()를 호출하기 위한 참조. 확인자/이의제기 연락처 발송 등은 단계가 없어 null.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = true)
    private HandoverStage handoverStage;

    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Column(name = "subject", length = 255)
    private String subject;

    // 호출자 트랜잭션에서 EmailBuilder로 미리 렌더링한 본문. 워커는 비즈니스 로직을 다시 돌리지 않는다.
    @Lob
    @Column(name = "body")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private EmailOutboxStatus status;

    @Column(name = "attempt_count")
    private Integer attemptCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public EmailOutbox(EmailLog emailLog, HandoverStage handoverStage, String recipientEmail,
                        String subject, String body) {
        this.emailLog = emailLog;
        this.handoverStage = handoverStage;
        this.recipientEmail = recipientEmail;
        this.subject = subject;
        this.body = body;
        this.status = EmailOutboxStatus.PENDING;
        this.attemptCount = 0;
        this.createdAt = LocalDateTime.now();
    }

    public void markSent() {
        this.status = EmailOutboxStatus.SENT;
        this.lastError = null;
    }

    // 재시도 가능한 실패 - PENDING을 유지해 다음 워커 주기에 다시 시도한다.
    public void recordFailedAttempt(String reason) {
        this.attemptCount = this.attemptCount + 1;
        this.lastError = reason;
    }

    // 더 이상 재시도하지 않는다 (최대 횟수 초과 또는 영구 반송) - DEAD로 전이해 다음 주기부터 조회되지 않게 한다.
    public void giveUp(String reason) {
        this.attemptCount = this.attemptCount + 1;
        this.lastError = reason;
        this.status = EmailOutboxStatus.DEAD;
    }
}
