package com.mamoki.ieojuda.domain.plan.entity;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.global.entity.BaseCreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "plans")
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

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "waiting_days")
    private Integer waitingDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    private PlanStatus status;

    @Builder
    public Plan(User user, String name, Integer waitingDays) {
        this.user = user;
        this.name = name;
        this.waitingDays = waitingDays;
        this.status = PlanStatus.DRAFT; // plan 생성하면 '작성중' 적용하기 위해
    }

    public void updateInfo(String name, Integer waitingDays) {
        this.name = name;
        this.waitingDays = waitingDays;
    }

    public void deactivate() {
        this.status = PlanStatus.DEACTIVATED;
    }
}