package com.mamoki.ieojuda.domain.recipient.entity;

import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "role_assigness")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignee_id") // 오탈자 DB 그대로 유지 (올바른 표기: assignee_id)
    private Long assigneeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "life_id", nullable = false)
    private LifeArea lifeArea;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "email", length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", length = 30)
    private RoleType roleType;

    @Column(name = "is_backup")
    private Boolean isBackup;

    @Enumerated(EnumType.STRING)
    @Column(name = "acceptance_status", length = 30)
    private AcceptanceStatus acceptanceStatus;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    /**
     * 사후 인계 단계에서 이 담당자에게 이메일이 발송된 시각(응답 대기 시작점).
     * max_wait_hours 이내에 완료/응답이 없으면 대체 담당자로 전환하는 기준.
     */
    @Column(name = "wait_at")
    private LocalDateTime waitAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "disclosure_scope", length = 30)
    private DisclosureScope disclosureScope;

    @Column(name = "max_wait_hours")
    private Integer maxWaitHours;

    @Column(name = "invite_token", length = 255)
    private String inviteToken;

    @Column(name = "invite_token_expires_at")
    private LocalDateTime inviteTokenExpiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "backup_for_id")
    private Recipient backupFor;

    // "역할 수락 이메일" 화면 - 담당자가 수락/거절 시 남기는 문의 사항 (선택 입력)
    @Column(name = "inquiry", length = 1000)
    private String inquiry;

    // 담당자 등록 시점 - 역할명, 담당자 이름, 이메일, 공개 범주, 최대 단계 대기 시간, 대체 담당자 입력
    @Builder
    public Recipient(Plan plan, LifeArea lifeArea, String name, String email, RoleType roleType,
                     Boolean isBackup, DisclosureScope disclosureScope,
                     Integer maxWaitHours, Recipient backupFor) {
        this.plan = plan;
        this.lifeArea = lifeArea;
        this.name = name;
        this.email = email;
        this.roleType = roleType;
        this.isBackup = isBackup;
        this.disclosureScope = disclosureScope;
        this.maxWaitHours = maxWaitHours;
        this.backupFor = backupFor;
        this.acceptanceStatus = AcceptanceStatus.PENDING;
    }

    // 초대 이메일 토큰 저장 (평문이 아닌 해시값만 저장, 만료 시각 함께 기록)
    public void issueInviteToken(String inviteTokenHash, LocalDateTime expiresAt) {
        this.inviteToken = inviteTokenHash;
        this.inviteTokenExpiresAt = expiresAt;
    }

    // 담당자 수락
    public void accept(String inquiry) {
        this.acceptanceStatus = AcceptanceStatus.ACCEPTED;
        this.acceptedAt = LocalDateTime.now();
        this.inquiry = inquiry;
    }

    // 담당자 거절
    public void decline(String inquiry) {
        this.acceptanceStatus = AcceptanceStatus.DECLINED;
        this.inquiry = inquiry;
    }

    // 초대 링크 만료
    public void expire() {
        this.acceptanceStatus = AcceptanceStatus.EXPIRED;
    }

    //이메일 변경 시 기존 수락 무효화 후 재검증 (명세서 "역할 수락 이메일/화면" 예외 처리)
    public void ReVerification(String newEmail) {
        this.email = newEmail;
        this.acceptanceStatus = AcceptanceStatus.PENDING;
        this.acceptedAt = null;
        this.inviteToken = null;
        this.inviteTokenExpiresAt = null;
    }

    // 사후 인계 단계에서 담당자에게 이메일을 보낸 시간 기록
    public void startWait() {
        this.waitAt = LocalDateTime.now();
    }

    // 수락 요청 재전송 - 새 토큰이 발급되므로 만료 상태를 수락 대기로 되돌린다
    public void resetAcceptance() {
        this.acceptanceStatus = AcceptanceStatus.PENDING;
        this.acceptedAt = null;
    }

    // "담당자 수정하기" - 이름/이메일 수정. 이메일이 바뀌면 기존 수락 상태와 초대 토큰을 무효화한다
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
}