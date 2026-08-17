package com.mamoki.ieojuda.domain.stage.entity;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDateTime;

@Entity
@Table(name = "handover_stages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HandoverStage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "stage_id")
    private UUID stageId;

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

    // issue #45 - 상태 전이표를 엔티티 내부에 강제한다 (ReleaseCase.ensureTransitionAllowed와 같은 목적)
    private void ensureTransitionAllowed(Set<HandoverStageStatus> allowedSourceStatuses) {
        if (!allowedSourceStatuses.contains(this.status)) {
            throw new CustomException(ErrorCode.HANDOVER_STAGE_INVALID_TRANSITION);
        }
    }

    // 발송준비 완료: PENDING -> READY
    public void markReady() {
        ensureTransitionAllowed(EnumSet.of(HandoverStageStatus.PENDING));
        this.status = HandoverStageStatus.READY;
    }

    // 발송됨: (PENDING|READY) -> SENT (최초 발송), FALLBACK -> SENT (대체 담당자에게 발송)
    public void send() {
        ensureTransitionAllowed(EnumSet.of(
                HandoverStageStatus.PENDING, HandoverStageStatus.READY, HandoverStageStatus.FALLBACK));
        this.status = HandoverStageStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    // 완료: SENT -> COMPLETED (담당자가 요구된 행동을 모두 마쳤을 때만)
    public void complete() {
        ensureTransitionAllowed(EnumSet.of(HandoverStageStatus.SENT));
        this.status = HandoverStageStatus.COMPLETED;
        this.confirmedAt = LocalDateTime.now();
    }

    // 반송: SENT -> BOUNCED
    public void bounce() {
        ensureTransitionAllowed(EnumSet.of(HandoverStageStatus.SENT));
        this.status = HandoverStageStatus.BOUNCED;
    }

    // 대체 담당자 전환됨 - 이 단계의 담당자를 대체 담당자로 교체하고 다시 발송 준비 상태로 되돌림.
    // (SENT|BOUNCED|FALLBACK) -> FALLBACK : 무응답(SENT)·영구반송(BOUNCED)뿐 아니라, 대체 담당자마저
    // 응답하지 않아 그 대체 담당자의 백업으로 다시 전환하는 연쇄 상황(FALLBACK)도 허용한다.
    public void fallbackTo(Recipient backupRecipient) {
        ensureTransitionAllowed(EnumSet.of(
                HandoverStageStatus.SENT, HandoverStageStatus.BOUNCED, HandoverStageStatus.FALLBACK));
        this.recipient = backupRecipient;
        this.status = HandoverStageStatus.FALLBACK;
    }

    // 차단: (SENT|BOUNCED|FALLBACK) -> BLOCKED (대체 담당자를 찾지 못해 더 이상 진행할 수 없을 때)
    public void block() {
        ensureTransitionAllowed(EnumSet.of(
                HandoverStageStatus.SENT, HandoverStageStatus.BOUNCED, HandoverStageStatus.FALLBACK));
        this.status = HandoverStageStatus.BLOCKED;
    }
}
