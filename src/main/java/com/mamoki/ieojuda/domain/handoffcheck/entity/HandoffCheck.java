package com.mamoki.ieojuda.domain.handoffcheck.entity;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.time.LocalDateTime;

@Entity
@Table(name = "handoff_checks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HandoffCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "check_id")
    private UUID checkId;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Builder
    public HandoffCheck(Plan plan) {
        this.plan = plan;
        this.sentAt = LocalDateTime.now();
    }
}