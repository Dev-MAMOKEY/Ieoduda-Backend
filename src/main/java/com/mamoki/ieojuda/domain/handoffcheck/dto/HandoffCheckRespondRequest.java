package com.mamoki.ieojuda.domain.handoffcheck.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// "선택형 생전 인계 점검" 응답 화면 - 이메일 도달·역할 수락·공개 범주 이해와 문의 사항만 수집
public record HandoffCheckRespondRequest(
        @Schema(description = "점검 이메일이 실제로 도달했는지 여부")
        @NotNull(message = "이메일 도달 여부를 선택해 주세요.") Boolean emailReachable,

        @Schema(description = "맡은 역할을 이해했는지 여부")
        @NotNull(message = "역할 이해 여부를 선택해 주세요.") Boolean roleUnderstood,

        @Schema(description = "공개 범주를 이해했는지 여부")
        @NotNull(message = "공개 범주 이해 여부를 선택해 주세요.") Boolean scopeUnderstood,

        @Schema(description = "문의 사항 (선택 입력)", example = "제 역할이 언제 시작되나요?")
        @Size(max = 1000, message = "문의 사항은 1000자 이하로 입력해 주세요.") String inquiry
) {
}
