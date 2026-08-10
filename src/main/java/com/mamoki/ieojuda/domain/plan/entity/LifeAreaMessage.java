package com.mamoki.ieojuda.domain.plan.entity;

import com.mamoki.ieojuda.global.entity.BaseCreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "life_area_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LifeAreaMessage extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20)
    private MessageRole role;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    // 삶의 구역 작성 대화(말풍선) 한 턴을 저장 - 사용자 발화/AI 응답이 순서대로 쌓임
    @Builder
    public LifeAreaMessage(Conversation conversation, MessageRole role, String content) {
        this.conversation = conversation;
        this.role = role;
        this.content = content;
    }
}
