package com.mamoki.ieojuda.domain.confirmer.entity;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "death_confirmers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Confirmer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "confirm_id")
    private Long confirmId;

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

    @Column(name = "invite_token", length = 255)
    private String inviteToken;

    @Column(name = "invite_token_expires_at")
    private LocalDateTime inviteTokenExpiresAt;

    // "지정확인자 수락 이메일" 화면 - 확인자가 수락/거절 시 남기는 문의 사항 (선택 입력)
    @Column(name = "inquiry", columnDefinition = "TEXT")
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

    // 초대 이메일 토큰 저장 (평문이 아닌 해시값만 저장, 만료 시각 함께 기록)
    public void issueInviteToken(String inviteTokenHash, LocalDateTime expiresAt) {
        this.inviteToken = inviteTokenHash;
        this.inviteTokenExpiresAt = expiresAt;
    }

    // 계정 이메일 변경 등 계정 탈취 대응 - 기존 초대 토큰으로는 더 이상 아무것도 할 수 없게 만든다
    // (수락 완료 후에는 만료 검사를 건너뛰는 개인 접근키로 쓰이므로, 만료가 아니라 값 자체를 지워야 진짜로 막힌다)
    public void invalidateInviteToken() {
        this.inviteToken = null;
        this.inviteTokenExpiresAt = null;
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
            this.inviteToken = null;
            this.inviteTokenExpiresAt = null;
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