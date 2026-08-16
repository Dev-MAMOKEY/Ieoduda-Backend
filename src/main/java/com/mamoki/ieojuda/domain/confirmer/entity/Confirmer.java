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
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "confirm_id") private Long confirmId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "plan_id", nullable = false) private Plan plan;
    @Column(name = "name", length = 100) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "relationship", length = 30) private Relationship relationship;
    @Column(name = "email", length = 255) private String email;
    @Enumerated(EnumType.STRING) @Column(name = "acceptance_status", length = 30) private AcceptanceStatus acceptanceStatus;
    @Column(name = "accepted_at") private LocalDateTime acceptedAt;
    @Enumerated(EnumType.STRING) @Column(name = "report_status", length = 30) private ReportStatus reportStatus;
    @Column(name = "reported_at") private LocalDateTime reportedAt;
    @Column(name = "reported_death_date") private LocalDate reportedDeathDate;
    @Column(name = "invite_token", length = 255) private String inviteToken;
    @Column(name = "invite_token_expires_at") private LocalDateTime inviteTokenExpiresAt;
    @Column(name = "inquiry", length = 1000) private String inquiry;

    @Builder
    public Confirmer(Plan plan, String name, Relationship relationship, String email) {
        this.plan = plan; this.name = name; this.relationship = relationship; this.email = email;
        this.acceptanceStatus = AcceptanceStatus.PENDING; this.reportStatus = ReportStatus.NOT_REPORTED;
    }
    public void issueInviteToken(String inviteTokenHash, LocalDateTime expiresAt) { this.inviteToken = inviteTokenHash; this.inviteTokenExpiresAt = expiresAt; }
    public void resetAcceptance() { this.acceptanceStatus = AcceptanceStatus.PENDING; this.acceptedAt = null; }
    public boolean updateContact(String name, String email) { this.name = name; boolean emailChanged = !this.email.equals(email); if (emailChanged) { this.email = email; this.acceptanceStatus = AcceptanceStatus.PENDING; this.acceptedAt = null; this.inviteToken = null; this.inviteTokenExpiresAt = null; } return emailChanged; }
    public void accept(String inquiry) { this.acceptanceStatus = AcceptanceStatus.ACCEPTED; this.acceptedAt = LocalDateTime.now(); this.inquiry = inquiry; }
    public void decline(String inquiry) { this.acceptanceStatus = AcceptanceStatus.DECLINED; this.inquiry = inquiry; }
    public void expire() { this.acceptanceStatus = AcceptanceStatus.EXPIRED; }
    public void report(LocalDate reportedDeathDate) { this.reportStatus = ReportStatus.REPORTED; this.reportedAt = LocalDateTime.now(); this.reportedDeathDate = reportedDeathDate; }
    public void markMatched() { this.reportStatus = ReportStatus.MATCHED; }
    public void markMismatched() { this.reportStatus = ReportStatus.MISMATCHED; }
}
