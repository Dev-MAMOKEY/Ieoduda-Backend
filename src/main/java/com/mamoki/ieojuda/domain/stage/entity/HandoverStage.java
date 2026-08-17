package com.mamoki.ieojuda.domain.stage.entity;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "handover_stages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HandoverStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stage_id")
    private Long stageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = true)
    private ReleaseCase releaseCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id", nullable = false) // issue #62 - role_assigness.assigne_id 오탈자 정리
    private Recipient recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "stage_order")
    private Integer stageOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    private HandoverStageStatus status;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    // 대체 담당자 전환·완료 처리가 동시에 들어오는 경우를 막기 위한 낙관적 잠금
    @Version
    @Column(name = "version")
    private Long version;

    @Builder
    public HandoverStage(Plan plan, Recipient recipient, Integer stageOrder) {
        this.plan = plan;
        this.recipient = recipient;
        this.stageOrder = stageOrder;
        this.status = HandoverStageStatus.PENDING; // 대기
    }

    //사망 확인 후 실제 사후 사건과 연결
    public void assignToCase(ReleaseCase releaseCase) {
        this.releaseCase = releaseCase;
    }

    // 발송준비 완료
    public void markReady() {
        this.status = HandoverStageStatus.READY;
    }

    // 발송됨
    public void send() {
        this.status = HandoverStageStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    // 완료
    public void complete() {
        this.status = HandoverStageStatus.COMPLETED;
        this.confirmedAt = LocalDateTime.now();
    }

    // 반송
    public void bounce() {
        this.status = HandoverStageStatus.BOUNCED;
    }

    // 대체 담당자 전환됨 - 이 단계의 담당자를 대체 담당자로 교체하고 다시 발송 준비 상태로 되돌림
    public void fallbackTo(Recipient backupRecipient) {
        this.recipient = backupRecipient;
        this.status = HandoverStageStatus.FALLBACK;
    }

    //차단
    public void block() {
        this.status = HandoverStageStatus.BLOCKED;
    }
}
