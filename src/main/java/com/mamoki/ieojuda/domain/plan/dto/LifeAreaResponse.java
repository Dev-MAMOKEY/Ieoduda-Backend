package com.mamoki.ieojuda.domain.plan.dto;

import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record LifeAreaResponse(
        @Schema(description = "삶의 구역 ID") Long lifeAreaId,
        @Schema(description = "구역 분류", example = "FAMILY", allowableValues = {"FAMILY", "RELATIONSHIP_CLEANUP", "WORK_CONTINUITY"}) String category,
        @Schema(description = "사용자가 작성한 원문 (아직 작성 전이면 null)") String rawText,
        @Schema(description = "AI 구조화 결과 검토 완료 여부") boolean reviewed,
        @Schema(description = "이 구역에 AI가 만든 항목 목록 (계획 생성 응답에서만 채워짐, 그 외 조회에서는 빈 목록)")
        List<LifeAreaTurnResponse.ItemResponse> items
) {
    public static LifeAreaResponse from(LifeArea lifeArea) {
        return from(lifeArea, List.of());
    }

    public static LifeAreaResponse from(LifeArea lifeArea, List<LifeAreaTurnResponse.ItemResponse> items) {
        return new LifeAreaResponse(
                lifeArea.getLifeId(),
                lifeArea.getCategory().name(),
                lifeArea.getRawText(),
                lifeArea.getReviewedAt() != null,
                items
        );
    }
}
