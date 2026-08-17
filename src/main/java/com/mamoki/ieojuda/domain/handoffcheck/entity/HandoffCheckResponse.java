package com.mamoki.ieojuda.domain.handoffcheck.entity;

import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.time.LocalDateTime;

@Entity
@Table(name = "handoff_check_responses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HandoffCheckResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "response_id")
    private UUID responseId;

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
    @JoinColumn(name = "assignee_id", nullable = false) // issue #62 - role_assigness.assigne_id 오탈자 정리
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
