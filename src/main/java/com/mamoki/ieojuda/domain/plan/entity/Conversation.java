package com.mamoki.ieojuda.domain.plan.entity;

import com.mamoki.ieojuda.global.entity.BaseCreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "conversation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "conversation_id")
    private Long conversationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Builder
    public Conversation(Plan plan) {
        this.plan = plan;
    }
}
