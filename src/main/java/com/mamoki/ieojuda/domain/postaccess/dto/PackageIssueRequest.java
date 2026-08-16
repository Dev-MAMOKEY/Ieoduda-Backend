package com.mamoki.ieojuda.domain.postaccess.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// "역할별 사후 패키지" 화면 - "문제 신고하기" 제출
public record PackageIssueRequest(
        @Schema(description = "문제가 발생한 항목 ID", example = "12")
        @NotNull(message = "문제를 신고할 항목을 선택해 주세요.") Long itemId,

        @Schema(description = "어떤 문제가 있었는지 설명", example = "단체 톡방에 들어갈 권한이 없어요")
        @NotBlank(message = "어떤 문제가 있었는지 입력해 주세요.")
        @Size(max = 1000, message = "문제 설명은 1000자를 넘을 수 없습니다.") String description
) {
}
