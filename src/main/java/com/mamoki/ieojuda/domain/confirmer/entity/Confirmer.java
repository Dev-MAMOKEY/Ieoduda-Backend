package com.mamoki.ieojuda.domain.confirmer.entity;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


import java.time.LocalDate;
import java.util.UUID;
import java.time.LocalDateTime;

@Entity
@Table(name = "death_confirmers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Confirmer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "confirm_id")
    private UUID confirmId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "name", length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship", length = 30)
    private Relationship relationship;

    @Column(name = "email", length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "acceptance_status", length = 30)
    private AcceptanceStatus acceptanceStatus;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_status", length = 30)
    private ReportStatus reportStatus;

    @Column(name = "reported_at")
    private LocalDateTime reportedAt;

    // "사망 신고 이메일" 화면 - 확인자가 입력한 사망일 (모르는 경우 null)
    @Column(name = "reported_death_date")
    private LocalDate reportedDeathDate;

    // issue #41 - 초대 이메일 발송 여부만 표시 (실제 토큰은 SecurityToken이 목적별로 별도 보관)
    @Column(name = "invite_sent")
    private Boolean inviteSent;

    // "지정확인자 수락 이메일" 화면 - 확인자가 수락/거절 시 남기는 문의 사항 (선택 입력)
    @Column(name = "inquiry", length = 1000)
    private String inquiry;

    @Builder
    public Confirmer(Plan plan, String name, Relationship relationship, String email) {
        this.plan = plan;
        this.name = name;
        this.relationship = relationship;
        this.email = email;
        this.acceptanceStatus = AcceptanceStatus.PENDING; // 처음 생성할떄 대기 상태
        this.reportStatus = ReportStatus.NOT_REPORTED;
    }

    // issue #41 - 초대 이메일 발송 완료 표시 (토큰 자체는 SecurityToken이 목적별로 별도 보관)
    public void markInviteSent() {
        this.inviteSent = true;
    }

    // 수락 요청 재전송 - 새 토큰이 발급되므로 만료 상태를 수락 대기로 되돌린다
    public void resetAcceptance() {
        this.acceptanceStatus = AcceptanceStatus.PENDING;
        this.acceptedAt = null;
    }

    // "확인자 수정하기" - 이름/이메일 수정. 이메일이 바뀌면 기존 수락 상태와 초대 토큰을 무효화한다
    public boolean updateContact(String name, String email) {
        this.name = name;
        boolean emailChanged = !this.email.equals(email);
        if (emailChanged) {
            this.email = email;
            this.acceptanceStatus = AcceptanceStatus.PENDING;
            this.acceptedAt = null;
        }
        return emailChanged;
    }

    //  수락 상태
    public void accept(String inquiry) {
        this.acceptanceStatus = AcceptanceStatus.ACCEPTED;
        this.acceptedAt = LocalDateTime.now();
        this.inquiry = inquiry;
    }
    // 거절 상태
    public void decline(String inquiry) {
        this.acceptanceStatus = AcceptanceStatus.DECLINED;
        this.inquiry = inquiry;
    }
    // 만료 상태
    public void expire() {
        this.acceptanceStatus = AcceptanceStatus.EXPIRED;
    }

    // 사망 신고 접수 (각 확인자 독립 신고, 사망일은 모를 경우 null)
    public void report(LocalDate reportedDeathDate) {
        this.reportStatus = ReportStatus.REPORTED;
        this.reportedAt = LocalDateTime.now();
        this.reportedDeathDate = reportedDeathDate;
    }

    // 다른 확인자 신고와 대상·사건이 일치한다고 판정
    public void markMatched() {
        this.reportStatus = ReportStatus.MATCHED;
    }

    // 다른 확인자 신고와 불일치 → 절차 중지, 운영 검토 전환
    public void markMismatched() {
        this.reportStatus = ReportStatus.MISMATCHED;
    }
}