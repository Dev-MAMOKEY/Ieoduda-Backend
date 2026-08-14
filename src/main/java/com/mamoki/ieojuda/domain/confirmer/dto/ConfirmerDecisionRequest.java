package com.mamoki.ieojuda.domain.confirmer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

// "지정확인자 수락 이메일" 화면 - 수락/거절 시 문의 사항은 선택 입력
public record ConfirmerDecisionRequest(
        @Schema(description = "문의 사항 (선택 입력)", example = "사망 확인은 어떻게 하면 되나요?")
        @Size(max = 1000, message = "문의 사항은 1000자 이하로 입력해 주세요.") String inquiry
) {
}
