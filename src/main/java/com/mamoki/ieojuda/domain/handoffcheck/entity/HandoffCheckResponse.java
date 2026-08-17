package com.mamoki.ieojuda.domain.handoffcheck.entity;

import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
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

    // "선택형 생전 인계 점검" 화면 - 담당자가 응답 시 남기는 문의 사항 (선택 입력)
    @Column(name = "inquiry", length = 1000)
    private String inquiry;

    @Column(name = "invite_token", length = 255)
    private String inviteToken;

    @Column(name = "invite_token_expires_at")
    private LocalDateTime inviteTokenExpiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_id", nullable = false)
    private HandoffCheck handoffCheck;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id", nullable = false) // issue #62 - role_assigness.assigne_id 오탈자 정리
    private Recipient recipient;

    // 점검 발송 시점 - 담당자별로 미응답 상태의 행을 먼저 생성해두고, 응답이 오면 respond()로 채운다
    @Builder
    public HandoffCheckResponse(HandoffCheck handoffCheck, Recipient recipient) {
        this.handoffCheck = handoffCheck;
        this.recipient = recipient;
    }

    // 점검 발송 토큰 저장 (평문이 아닌 해시값만 저장, 만료 시각 함께 기록)
    public void issueInviteToken(String inviteTokenHash, LocalDateTime expiresAt) {
        this.inviteToken = inviteTokenHash;
        this.inviteTokenExpiresAt = expiresAt;
    }

    // 만료·응답 완료 등으로 더 이상 쓸 수 없게 된 토큰을 지운다
    public void invalidateInviteToken() {
        this.inviteToken = null;
        this.inviteTokenExpiresAt = null;
    }

    // 담당자가 점검에 응답 - 응답과 동시에 토큰을 무효화해 링크 재사용을 막는다
    public void respond(Boolean emailReached, Boolean roleUnderstood, Boolean disclosureUnderstood, String inquiry) {
        this.emailReached = emailReached;
        this.roleUnderstood = roleUnderstood;
        this.disclosureUnderstood = disclosureUnderstood;
        this.inquiry = inquiry;
        this.respondedAt = LocalDateTime.now();
        invalidateInviteToken();
    }
}
