package com.mamoki.ieojuda.domain.releasecase.entity;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanVersion;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "release_cases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReleaseCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "case_id")
    private Long caseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private PlanVersion planVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    private ReleaseCaseStatus status;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "evidence_approved_at")
    private LocalDateTime evidenceApprovedAt;

    @Column(name = "waiting_ends_at")
    private LocalDateTime waitingEndsAt;

    @Column(name = "frozen")
    private Boolean frozen;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Builder
    public ReleaseCase(Plan plan, PlanVersion planVersion) {
        this.plan = plan;
        this.planVersion = planVersion;
        this.status = ReleaseCaseStatus.REPORT_PENDING; // 신고 대기 상태
        this.frozen = false;
    }

    // "이메일 발송 감사" 화면 - 운영자가 발송 절차 전체를 동결
    public void freeze() {
        this.frozen = true;
    }

    public void unfreeze() {
        this.frozen = false;
    }
}