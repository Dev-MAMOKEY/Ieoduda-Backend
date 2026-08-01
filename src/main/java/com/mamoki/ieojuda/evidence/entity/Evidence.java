package com.mamoki.ieojuda.evidence.entity;

import com.mamoki.ieojuda.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.partner.entity.PartnerReviewer;
import com.mamoki.ieojuda.plan.entity.Plan;
import com.mamoki.ieojuda.releasecase.entity.ReleaseCase;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "evidences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "evidence_id")
    private Long evidenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirm_id", nullable = false)
    private Confirmer confirmer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private ReleaseCase releaseCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = true)
    private PartnerReviewer reviewer;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", length = 30)
    private EvidenceReviewStatus reviewStatus;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "delete_scheduled_at")
    private LocalDateTime deleteScheduledAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "integrity_hash", length = 255)
    private String integrityHash;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "storage_key", length = 500, nullable = false)
    private String storageKey;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Builder
    public Evidence(Confirmer confirmer, Plan plan, ReleaseCase releaseCase,
                    String storageKey, String fileName, String mimeType, String integrityHash) {
        this.confirmer = confirmer;
        this.plan = plan;
        this.releaseCase = releaseCase;
        this.storageKey = storageKey;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.integrityHash = integrityHash;
        this.submittedAt = LocalDateTime.now();
        this.reviewStatus = EvidenceReviewStatus.PENDING;
    }



    // 승인. 검토 완료일 기준 30일 이내 삭제 규정에 따라 삭제 예정일 자동 계산 - 명세서상 30일로 임의 지정
    public void approve() {
        this.reviewStatus = EvidenceReviewStatus.APPROVED;
        this.reviewedAt = LocalDateTime.now();
        this.deleteScheduledAt = this.reviewedAt.plusDays(30);
    }

    // 반려. 검토 완료 시점 기준 삭제 일정 부여  - 명세서 상 30일로 임의 지정
    public void reject(String failureReason) {
        this.reviewStatus = EvidenceReviewStatus.REJECTED;
        this.reviewedAt = LocalDateTime.now();
        this.failureReason = failureReason;
        this.deleteScheduledAt = this.reviewedAt.plusDays(30);
    }

    public void reAdditionalInfo() {
        this.reviewStatus = EvidenceReviewStatus.ADDITIONAL_INFO_REQUESTED;
    }

    public void markDeleted() {
        this.deletedAt = LocalDateTime.now();
    }
}
