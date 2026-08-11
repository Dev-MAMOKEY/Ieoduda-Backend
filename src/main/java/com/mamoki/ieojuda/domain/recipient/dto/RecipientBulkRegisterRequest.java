package com.mamoki.ieojuda.domain.recipient.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

// "역할 담당자 등록" 화면 - 승인된 항목 개수만큼 담당자를 한 번에 등록하고 수락 이메일을 발송
public record RecipientBulkRegisterRequest(
        @Schema(description = "등록할 담당자 목록 (승인된 항목 개수만큼)")
        @NotEmpty(message = "등록할 담당자를 한 명 이상 입력해 주세요.")
        @Valid List<RecipientRegisterRequest> recipients
) {
}
