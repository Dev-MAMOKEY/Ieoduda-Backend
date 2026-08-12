package com.mamoki.ieojuda.domain.confirmer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

// "지정 확인자 등록" 화면 - "확인자 등록하기"로 늘린 폼 전체를 한 번에 등록하고 수락 이메일을 발송
public record ConfirmerBulkRegisterRequest(
        @Schema(description = "등록할 확인자 목록")
        @NotEmpty(message = "등록할 확인자를 한 명 이상 입력해 주세요.")
        @Valid List<ConfirmerRegisterRequest> confirmers
) {
}
