package com.mamoki.ieojuda.domain.releasecase.entity;

import com.mamoki.ieojuda.domain.partner.entity.ExternalPartner;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanVersion;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.time.LocalDateTime;

@Entity
@Table(name = "release_cases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReleaseCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "case_id")
    private UUID caseId;

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

    // issue #59 - 이 사건의 증빙 검토를 담당하는 외부 파트너사. 운영자가 수동으로 배정하며(#59 시점 기준
    // 조직/업무 자동 배정 규칙은 기획에 없음), 배정 전까지는 어떤 파트너 검토자도 이 사건을 조작할 수 없다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_partner_id")
    private ExternalPartner assignedPartner;

    // 동시에 여러 요청(파트너 승인, 스케줄러 등)이 같은 사건 상태를 전이시키려 할 때 뒤늦은 쪽을 밀어내기 위한 낙관적 잠금
    @Version
    @Column(name = "version")
    private Long version;

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

    // issue #59 - 이 사건의 증빙 검토를 특정 외부 파트너사에 배정(운영자 수동 배정)
    public void assignPartner(ExternalPartner partner) {
        this.assignedPartner = partner;
    }

    public void unfreeze() {
        this.frozen = false;
    }

    // 두 확인자의 신고 내용이 일치해 사건이 생성된 직후 - 신고 확인됨 상태로 전이
    public void confirmReport() {
        this.confirmedAt = LocalDateTime.now();
        this.status = ReleaseCaseStatus.REPORT_CONFIRMED;
    }

    // 신고 확인 직후 - 증빙 제출 대기 상태로 전이
    public void awaitEvidence() {
        this.status = ReleaseCaseStatus.EVIDENCE_PENDING;
    }

    // 증빙이 처음 제출되면 검토 중 상태로 전이
    public void startEvidenceReview() {
        this.status = ReleaseCaseStatus.EVIDENCE_REVIEWING;
    }

    // 외부 파트너가 증빙을 승인하면, 계획의 대기 기간만큼 실행을 미루는 대기 상태로 전이
    // 작성자가 대기·이의제기 설정을 아직 안 했을 수 있어 waitingDays가 null이면 기본값 7일을 적용한다
    public void approveEvidenceAndStartWaiting(Integer waitingDays) {
        this.evidenceApprovedAt = LocalDateTime.now();
        this.status = ReleaseCaseStatus.WAITING;
        this.waitingEndsAt = this.evidenceApprovedAt.plusDays(waitingDays != null ? waitingDays : 7);
    }

    // 외부 파트너가 증빙을 반려
    public void rejectEvidence() {
        this.status = ReleaseCaseStatus.EVIDENCE_REJECTED;
    }

    // 작성자 본인이 "본인 확인 후 취소하기"를 눌렀을 때 - 절차 전체를 즉시 취소
    public void cancel() {
        this.status = ReleaseCaseStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }

    // 이의 제기가 접수되면 확인될 때까지 자동으로 절차를 멈춤
    public void raiseDispute() {
        this.status = ReleaseCaseStatus.DISPUTED;
    }

    // 대기 기간이 지나 스케줄러가 자동으로 발송 단계에 진입
    public void startReleasing() {
        this.status = ReleaseCaseStatus.RELEASING;
    }

    // issue #78 - 마지막 발송 단계까지 완료되면 사건 전체를 완료 처리
    public void complete() {
        this.status = ReleaseCaseStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }
}