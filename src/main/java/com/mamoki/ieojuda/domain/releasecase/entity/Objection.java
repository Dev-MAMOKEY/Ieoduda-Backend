package com.mamoki.ieojuda.domain.releasecase.entity;

import com.mamoki.ieojuda.domain.confirmer.entity.DisputeContact;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "objections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Objection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "objection_id")
    private Long objectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private ReleaseCase releaseCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    private DisputeContact disputeContact;

    @Column(name = "raised_at")
    private LocalDateTime raisedAt;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    private ObjectionStatus status;

    @Builder
    public Objection(Plan plan, ReleaseCase releaseCase, DisputeContact disputeContact, String reason) {
        this.plan = plan;
        this.releaseCase = releaseCase;
        this.disputeContact = disputeContact;
        this.reason = reason;
        this.raisedAt = LocalDateTime.now();
        this.status = ObjectionStatus.RAISED; // 접수됨
    }

    // 검토중
    public void startReview() {
        this.status = ObjectionStatus.UNDER_REVIEW;
    }

    // 이의 해소
    public void resolve() {
        this.status = ObjectionStatus.RESOLVED;
    }

    // 기각 됨
    public void dismiss() {
        this.status = ObjectionStatus.DISMISSED;
    }
}
