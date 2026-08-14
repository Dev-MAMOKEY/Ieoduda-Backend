package com.mamoki.ieojuda.domain.recipient.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

// "역할 수락 이메일" 화면 - 수락/거절 시 문의 사항은 선택 입력
public record RecipientInviteDecisionRequest(
        @Schema(description = "문의 사항 (선택 입력)", example = "부고 전달 범위가 궁금해요.")
        @Size(max = 1000, message = "문의 사항은 1000자 이하로 입력해 주세요.") String inquiry
) {
}
