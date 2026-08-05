package com.mamoki.ieojuda.domain.plan.dto;

import com.mamoki.ieojuda.domain.plan.entity.LifeAreaMessage;
import com.mamoki.ieojuda.domain.plan.entity.MessageRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// 대화 작성 화면에 말풍선 하나를 그리기 위한 응답
public record LifeAreaMessageResponse(
        @Schema(description = "메세지 ID") Long messageId,
        @Schema(description = "발화자 (USER 또는 ASSISTANT)") MessageRole role,
        @Schema(description = "메세지 내용") String content,
        @Schema(description = "작성 시각") LocalDateTime createdAt
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
