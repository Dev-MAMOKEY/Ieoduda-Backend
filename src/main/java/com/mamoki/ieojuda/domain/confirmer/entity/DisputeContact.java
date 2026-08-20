package com.mamoki.ieojuda.domain.confirmer.entity;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.time.LocalDateTime;

@Entity
@Table(name = "dispute_contacts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DisputeContact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "contact_id")
    private UUID contactId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "is_verified")
    private Boolean isVerified;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "invite_token", length = 255)
    private String inviteToken;

    @Column(name = "invite_token_expires_at")
    private LocalDateTime inviteTokenExpiresAt;

    @Builder
    public DisputeContact(Plan plan, String email, String name) {
        this.plan = plan;
        this.email = email;
        this.name = name;
        this.isVerified = false;
    }

    // 검증 이메일 발송 시 토큰(해시) 저장
    public void issueInviteToken(String inviteTokenHash, LocalDateTime expiresAt) {
        this.inviteToken = inviteTokenHash;
        this.inviteTokenExpiresAt = expiresAt;
    }

    // 계정 이메일 변경 등 계정 탈취 대응 - 기존 초대 토큰으로는 더 이상 아무것도 할 수 없게 만든다
    public void invalidateInviteToken() {
        this.inviteToken = null;
        this.inviteTokenExpiresAt = null;
    }

    // 이메일 검증 완료
    public void verify() {
        this.isVerified = true;
        this.verifiedAt = LocalDateTime.now();
    }

    // "대기 이의제기 수정" 화면 - 이름/이메일 수정
    public void updateContact(String name, String email) {
        this.name = name;
        this.email = email;
    }

    // 이메일이 바뀌면 이전 검증은 더 이상 유효하지 않으므로 다시 검증받아야 함
    public void resetVerification() {
        this.isVerified = false;
        this.verifiedAt = null;
    }
}
