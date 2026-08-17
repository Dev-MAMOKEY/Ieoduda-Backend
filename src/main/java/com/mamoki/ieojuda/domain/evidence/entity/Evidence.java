package com.mamoki.ieojuda.domain.evidence.entity;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.partner.entity.PartnerReviewer;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
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

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "storage_key", length = 500, nullable = false)
    private String storageKey;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    // issue #88 - 증빙이 사망진단서/사망신고서/검안서 중 무엇인지. 파일명은 제출자가 임의로 정하므로 신뢰할 수 없어 별도 필드로 받는다.
    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", length = 30, nullable = false)
    private EvidenceType evidenceType;

    // 파트너 검토 결정이 동시에 두 번 들어오는 경우를 막기 위한 낙관적 잠금
    @Version
    @Column(name = "version")
    private Long version;

    @Builder
    public Evidence(Confirmer confirmer, Plan plan, ReleaseCase releaseCase,
                    String storageKey, String fileName, String mimeType, String integrityHash,
                    EvidenceType evidenceType) {
        this.confirmer = confirmer;
        this.plan = plan;
        this.releaseCase = releaseCase;
        this.storageKey = storageKey;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.integrityHash = integrityHash;
        this.evidenceType = evidenceType;
        this.submittedAt = LocalDateTime.now();
        this.reviewStatus = EvidenceReviewStatus.PENDING;
    }



    // 이 증빙을 검토할 파트너 검토자 배정 (검토 결과 제출 시 함께 기록)
    public void assignReviewer(PartnerReviewer reviewer) {
        this.reviewer = reviewer;
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
        this.failureReason = null;
    }

    // "증빙 삭제 감사" 화면 - 자동 삭제 실패 시 사유 기록 (재처리 요청 대상)
    public void markDeleteFailed(String failureReason) {
        this.failureReason = failureReason;
    }
}
