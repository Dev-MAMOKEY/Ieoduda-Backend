package com.mamoki.ieojuda.domain.plan.dto;

import com.mamoki.ieojuda.domain.plan.entity.LifeAreaCategory;
import io.swagger.v3.oas.annotations.media.Schema;

// "계획 홈" 화면 - 구역 카드 하나 (전체 항목 목록이 아니라 개수·작성 여부만)
public record LifeAreaSummaryResponse(
        @Schema(description = "구역 분류", example = "FAMILY", allowableValues = {"FAMILY", "RELATIONSHIP_CLEANUP", "WORK_CONTINUITY"}) String category,
        @Schema(description = "구역 표시 이름") String label,
        @Schema(description = "구역 한 줄 설명") String summary,
        @Schema(description = "이 구역에 쌓인 항목 수") int itemCount,
        @Schema(description = "항목을 하나라도 작성했는지 여부") boolean isWritten
) {
    public static LifeAreaSummaryResponse from(LifeAreaResponse lifeAreaResponse) {
        LifeAreaCategory category = LifeAreaCategory.valueOf(lifeAreaResponse.category());
        int itemCount = lifeAreaResponse.items().size();
        return new LifeAreaSummaryResponse(category.name(), category.label(), category.summary(), itemCount, itemCount > 0);
    }
}
