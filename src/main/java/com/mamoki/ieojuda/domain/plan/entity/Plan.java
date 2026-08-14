package com.mamoki.ieojuda.domain.plan.entity;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.global.entity.BaseCreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "plans", uniqueConstraints = @UniqueConstraint(name = "UQ_plans_user_id", columnNames = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Plan extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    private Long planId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    private PlanStatus status;

    // 명세서 "대기·이의 제기 설정" 화면 - 증빙 승인 후 실제 발송까지 기다리는 기간(7/14/21일)
    @Column(name = "waiting_days")
    private Integer waitingDays;

    // 실행(사망 신고) 접수 시 가장 먼저 경고 메일을 받을 본인 주소 - 로그인 이메일과 별개로 검증 필요
    @Column(name = "self_warning_email", length = 255)
    private String selfWarningEmail;

    @Column(name = "self_warning_email_verified")
    private Boolean selfWarningEmailVerified;

    @Column(name = "self_warning_verify_token", length = 255)
    private String selfWarningVerifyToken;

    @Column(name = "self_warning_verify_token_expires_at")
    private LocalDateTime selfWarningVerifyTokenExpiresAt;

    // "실행 순서 점검" 화면 - 충돌 없이 순서를 확정한 시각. 순서가 다시 바뀌면 null로 되돌아가 재확정이 필요하다
    @Column(name = "order_confirmed_at")
    private LocalDateTime orderConfirmedAt;

    @Builder
    public Plan(User user) {
        this.user = user;
        this.status = PlanStatus.DRAFT; // plan 생성하면 '작성중' 적용하기 위해
        this.selfWarningEmailVerified = false;
    }

    public void deactivate() {
        this.status = PlanStatus.DEACTIVATED;
    }

    public void updateWaitingDays(Integer waitingDays) {
        this.waitingDays = waitingDays;
    }

    // 본인 경고 이메일 등록/재등록 - 새로 등록하면 이전 검증 상태는 초기화되고 다시 검증해야 함
    public void requestSelfWarningEmailVerification(String email, String tokenHash, LocalDateTime expiresAt) {
        this.selfWarningEmail = email;
        this.selfWarningEmailVerified = false;
        this.selfWarningVerifyToken = tokenHash;
        this.selfWarningVerifyTokenExpiresAt = expiresAt;
    }

    public void verifySelfWarningEmail() {
        this.selfWarningEmailVerified = true;
    }

    // 순서 충돌이 없을 때만 서비스에서 호출 - 실행 순서를 확정
    public void confirmOrder() {
        this.orderConfirmedAt = LocalDateTime.now();
    }

    // 확정 후 순서가 다시 바뀌면 재확정이 필요하므로 되돌린다
    public void resetOrderConfirmation() {
        this.orderConfirmedAt = null;
    }
}