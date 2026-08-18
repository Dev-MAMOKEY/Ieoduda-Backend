package com.mamoki.ieojuda.domain.plan.dto;

import com.mamoki.ieojuda.domain.plan.entity.ItemStatus;
import com.mamoki.ieojuda.domain.plan.entity.LifeAreaCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.stream.Collectors;

// "계획 홈" 화면 - 구역 카드 하나 (전체 항목 목록이 아니라 개수·작성 여부만)
public record LifeAreaSummaryResponse(
        @Schema(description = "구역 분류", example = "FAMILY", allowableValues = {"FAMILY", "RELATIONSHIP_CLEANUP", "WORK_CONTINUITY"}) String category,
        @Schema(description = "구역 표시 이름") String label,
        @Schema(description = "구역 한 줄 설명 - 승인된 항목의 title/content를 조합, 승인된 항목이 없으면 null", example = "인스타그램 탈퇴 처리, 카카오톡 탈퇴 처리") String summary,
        @Schema(description = "이 구역에서 승인된 항목 수") int itemCount,
        @Schema(description = "승인된 항목을 하나라도 작성했는지 여부") boolean isWritten
) {
    private static final int SUMMARY_ITEM_LIMIT = 3;

    public static LifeAreaSummaryResponse from(LifeAreaResponse lifeAreaResponse) {
        LifeAreaCategory category = LifeAreaCategory.valueOf(lifeAreaResponse.category());
        List<ItemResponse> approvedItems = lifeAreaResponse.items().stream()
                .filter(item -> ItemStatus.APPROVED.name().equals(item.status()))
                .toList();
        int itemCount = approvedItems.size();
        String summary = itemCount == 0 ? null : buildSummary(approvedItems);
        return new LifeAreaSummaryResponse(category.name(), category.label(), summary, itemCount, itemCount > 0);
    }

    private static String buildSummary(List<ItemResponse> approvedItems) {
        String joined = approvedItems.stream()
                .limit(SUMMARY_ITEM_LIMIT)
                .map(item -> item.title() + " " + item.content())
                .collect(Collectors.joining(", "));
        int remaining = approvedItems.size() - SUMMARY_ITEM_LIMIT;
        return remaining > 0 ? joined + " 외 " + remaining + "건" : joined;
    }
}
