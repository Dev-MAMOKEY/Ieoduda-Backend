package com.mamoki.ieojuda.domain.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

// "실행 순서 점검" 화면 - 드래그로 바뀐 새 순서 전체를 한 번에 전달
public record ItemReorderRequest(
        @Schema(description = "새 실행 순서대로 나열한 항목 ID 목록 (배열 위치 = 새 sortOrder)")
        @NotEmpty(message = "순서를 지정할 항목을 한 개 이상 입력해 주세요.") List<Long> itemIds
) {
}
