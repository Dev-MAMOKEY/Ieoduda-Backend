package com.mamoki.ieojuda.domain.confirmer.entity;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


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

    @Column(name = "invite_token", length = 255)
    private String inviteToken;

    @Column(name = "invite_token_expires_at")
    private LocalDateTime inviteTokenExpiresAt;

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

    //  수락 상태
    public void accept() {
        this.acceptanceStatus = AcceptanceStatus.ACCEPTED;
        this.acceptedAt = LocalDateTime.now();
    }
    // 거절 상태
    public void decline() {
        this.acceptanceStatus = AcceptanceStatus.DECLINED;
    }
    // 만료 상태
    public void expire() {
        this.acceptanceStatus = AcceptanceStatus.EXPIRED;
    }

    // 사망 신고 접수 ( 각 확인자 독립 신고)
    public void report() {
        this.reportStatus = ReportStatus.REPORTED;
        this.reportedAt = LocalDateTime.now();
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