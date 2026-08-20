package com.mamoki.ieojuda.domain.plan.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "life_areas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LifeArea {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "life_id")
    private UUID lifeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30)
    private LifeAreaCategory category;

    @Builder
    public LifeArea(Plan plan, Conversation conversation, LifeAreaCategory category) {
        this.plan = plan;
        this.conversation = conversation;
        this.category = category;
    }
}
