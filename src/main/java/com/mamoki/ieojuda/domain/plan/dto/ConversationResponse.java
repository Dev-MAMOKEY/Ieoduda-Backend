package com.mamoki.ieojuda.domain.plan.dto;

import com.mamoki.ieojuda.domain.plan.entity.Conversation;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// 대화 세션 하나(예: "처음 계획 채팅", "나중에 수정하러 들어온 채팅") - 언제 대화했는지 기록용
public record ConversationResponse(
        @Schema(description = "대화 세션 ID") Long conversationId,
        @Schema(description = "세션 시작 시각") LocalDateTime createdAt
) {
    public static ConversationResponse from(Conversation conversation) {
        return new ConversationResponse(conversation.getConversationId(), conversation.getCreatedAt());
    }
}
