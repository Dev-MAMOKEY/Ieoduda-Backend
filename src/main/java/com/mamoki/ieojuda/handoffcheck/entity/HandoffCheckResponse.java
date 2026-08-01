package com.mamoki.ieojuda.handoffcheck.entity;

import com.mamoki.ieojuda.recipient.entity.Recipient;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "handoff_check_responses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HandoffCheckResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "response_id")
    private Long responseId;

    @Column(name = "email_reached")
    private Boolean emailReached;

    @Column(name = "role_understood")
    private Boolean roleUnderstood;

    @Column(name = "disclosure_understood")
    private Boolean disclosureUnderstood;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_id", nullable = false)
    private HandoffCheck handoffCheck;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigne_id", nullable = false) // role_assigness.assigne_id 오탈자 그대로
    private Recipient recipient;

    @Builder
    public HandoffCheckResponse(HandoffCheck handoffCheck, Recipient recipient,
                                Boolean emailReached, Boolean roleUnderstood,
                                Boolean disclosureUnderstood) {
        this.handoffCheck = handoffCheck;
        this.recipient = recipient;
        this.emailReached = emailReached;
        this.roleUnderstood = roleUnderstood;
        this.disclosureUnderstood = disclosureUnderstood;
        this.respondedAt = LocalDateTime.now();
    }
}
