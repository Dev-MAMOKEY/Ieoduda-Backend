package com.mamoki.ieojuda.domain.plan.dto;

import com.mamoki.ieojuda.domain.plan.entity.LifeAreaMessage;
import com.mamoki.ieojuda.domain.plan.entity.MessageRole;

import java.time.LocalDateTime;

// 대화 작성 화면에 말풍선 하나를 그리기 위한 응답
public record LifeAreaMessageResponse(
        Long messageId,
        MessageRole role,
        String content,
        LocalDateTime createdAt
) {
    public static LifeAreaMessageResponse from(LifeAreaMessage message) {
        return new LifeAreaMessageResponse(
                message.getMessageId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
