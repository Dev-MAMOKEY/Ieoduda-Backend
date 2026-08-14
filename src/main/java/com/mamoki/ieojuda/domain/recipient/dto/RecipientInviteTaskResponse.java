package com.mamoki.ieojuda.domain.recipient.dto;

import com.mamoki.ieojuda.domain.plan.entity.Item;
import io.swagger.v3.oas.annotations.media.Schema;

// "역할 수락 이메일" 화면 - 담당자에게 배정된 항목 한 건의 제목/할 일만 노출 (명세 "실제 패키지 전문은 공개하지 않습니다")
public record RecipientInviteTaskResponse(
        @Schema(description = "짧은 제목 - 대상 채널/계정명", example = "인스타그램") String title,
        @Schema(description = "그 채널에 대해 할 일", example = "탈퇴 처리") String content
) {
    public static RecipientInviteTaskResponse from(Item item) {
        return new RecipientInviteTaskResponse(item.getTitle(), item.getContent());
    }
}
