package com.mamoki.ieojuda.domain.releasecase.entity;

import com.mamoki.ieojuda.domain.partner.entity.ExternalPartner;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanVersion;
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

// issue #45 - 취소·분쟁 사건이 파트너 재판정으로 WAITING에 되살아나거나 상태가 반복·역행하지 않도록,
// 모든 상태 변경 메서드가 "현재 상태에서 허용되는 전이인지"를 스스로 검증한다(서비스 계층에 맡기지 않고
// 엔티티 내부에서 강제). 허용 전이표는 각 메서드 위 주석에 "from → to"로 명시한다.
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

    // "이메일 발송 감사" 화면 - 운영자가 발송 절차 전체를 동결 (status 전이표와 별개인 보조 플래그)
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

    // REPORT_PENDING → REPORT_CONFIRMED : 두 확인자의 신고 내용이 일치해 사건이 생성된 직후
    public void confirmReport() {
        ensureTransitionAllowed(EnumSet.of(ReleaseCaseStatus.REPORT_PENDING));
        this.confirmedAt = LocalDateTime.now();
        this.status = ReleaseCaseStatus.REPORT_CONFIRMED;
    }

    // REPORT_CONFIRMED → EVIDENCE_PENDING : 신고 확인 직후 증빙 제출 대기로 전이
    public void awaitEvidence() {
        ensureTransitionAllowed(EnumSet.of(ReleaseCaseStatus.REPORT_CONFIRMED));
        this.status = ReleaseCaseStatus.EVIDENCE_PENDING;
    }

    // EVIDENCE_PENDING → EVIDENCE_REVIEWING : 증빙이 처음 제출되면 검토 중 상태로 전이
    public void startEvidenceReview() {
        ensureTransitionAllowed(EnumSet.of(ReleaseCaseStatus.EVIDENCE_PENDING));
        this.status = ReleaseCaseStatus.EVIDENCE_REVIEWING;
    }

    // issue #45 - EVIDENCE_REVIEWING → EVIDENCE_APPROVED : 사건에 필요한 증빙(매칭된 확인자별 1건) 중
    // 일부만 승인된 상태 - 아직 전부 승인되지 않아 WAITING으로는 넘어가지 않는다.
    public void markEvidencePartiallyApproved() {
        ensureTransitionAllowed(EnumSet.of(ReleaseCaseStatus.EVIDENCE_REVIEWING));
        this.status = ReleaseCaseStatus.EVIDENCE_APPROVED;
    }

    // EVIDENCE_REVIEWING/EVIDENCE_APPROVED → WAITING : 필요한 증빙이 전부 승인되면, 계획에 설정된 대기
    // 기간만큼 실행을 미루는 대기 상태로 전이한다. CANCELED·DISPUTED에서는 절대 이 상태로 넘어갈 수 없다
    // (issue #45 핵심 완료조건 - 취소·분쟁 사건이 파트너 재판정으로 되살아나지 않아야 한다).
    // 작성자가 대기·이의제기 설정을 아직 안 했을 수 있어 waitingDays가 null이면 기본값 7일을 적용한다
    public void approveEvidenceAndStartWaiting(Integer waitingDays) {
        ensureTransitionAllowed(EnumSet.of(ReleaseCaseStatus.EVIDENCE_REVIEWING, ReleaseCaseStatus.EVIDENCE_APPROVED));
        this.evidenceApprovedAt = LocalDateTime.now();
        this.status = ReleaseCaseStatus.WAITING;
        this.waitingEndsAt = this.evidenceApprovedAt.plusDays(waitingDays != null ? waitingDays : 7);
    }

    // EVIDENCE_REVIEWING/EVIDENCE_APPROVED → EVIDENCE_REJECTED : 외부 파트너가 증빙을 반려
    public void rejectEvidence() {
        ensureTransitionAllowed(EnumSet.of(ReleaseCaseStatus.EVIDENCE_REVIEWING, ReleaseCaseStatus.EVIDENCE_APPROVED));
        this.status = ReleaseCaseStatus.EVIDENCE_REJECTED;
    }

    // 진행 중인 어떤 상태에서도 → CANCELED : 작성자 본인이 "본인 확인 후 취소하기"를 눌렀을 때 절차 전체를
    // 즉시 취소한다. 이미 발송이 시작됐거나(RELEASING) 끝난(COMPLETED) 사건, 이미 취소된 사건은 대상이 아니다.
    public void cancel() {
        ensureTransitionAllowed(EnumSet.of(
                ReleaseCaseStatus.REPORT_PENDING, ReleaseCaseStatus.REPORT_CONFIRMED,
                ReleaseCaseStatus.EVIDENCE_PENDING, ReleaseCaseStatus.EVIDENCE_REVIEWING,
                ReleaseCaseStatus.EVIDENCE_APPROVED, ReleaseCaseStatus.EVIDENCE_REJECTED,
                ReleaseCaseStatus.WAITING, ReleaseCaseStatus.DISPUTED));
        this.status = ReleaseCaseStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }

    // WAITING → DISPUTED : 이의 제기는 대기 기간(RAISE_OBJECTION 토큰이 유효한 창구) 중에만 접수된다.
    // 접수되면 확인될 때까지 자동으로 절차를 멈춘다.
    public void raiseDispute() {
        ensureTransitionAllowed(EnumSet.of(ReleaseCaseStatus.WAITING));
        this.status = ReleaseCaseStatus.DISPUTED;
    }

    // WAITING → RELEASING : 대기 기간이 지나 스케줄러가 자동으로 발송 단계에 진입. 스케줄러 쿼리가 이미
    // status='WAITING'만 조회하지만(issue #57), 다른 경로로 잘못 호출되는 것까지 여기서 한 번 더 막는다.
    public void startReleasing() {
        ensureTransitionAllowed(EnumSet.of(ReleaseCaseStatus.WAITING));
        this.status = ReleaseCaseStatus.RELEASING;
    }

    // RELEASING → COMPLETED : issue #78 - 마지막 발송 단계까지 완료되면 사건 전체를 완료 처리
    public void complete() {
        ensureTransitionAllowed(EnumSet.of(ReleaseCaseStatus.RELEASING));
        this.status = ReleaseCaseStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    private void ensureTransitionAllowed(Set<ReleaseCaseStatus> allowedSourceStatuses) {
        if (!allowedSourceStatuses.contains(this.status)) {
            throw new CustomException(ErrorCode.RELEASE_CASE_INVALID_TRANSITION);
        }
    }
}
