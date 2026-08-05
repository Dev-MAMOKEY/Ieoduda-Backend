package com.mamoki.ieojuda.domain.plan.dto;

import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import io.swagger.v3.oas.annotations.media.Schema;

public record LifeAreaResponse(
        @Schema(description = "삶의 구역 ID") Long lifeAreaId,
        @Schema(description = "구역 분류", example = "FAMILY", allowableValues = {"FAMILY", "RELATIONSHIP_CLEANUP", "WORK_CONTINUITY"}) String category,
        @Schema(description = "사용자가 작성한 원문 (아직 작성 전이면 null)") String rawText,
        @Schema(description = "AI 구조화 결과 검토 완료 여부") boolean reviewed
) {
    public static LifeAreaResponse from(LifeArea lifeArea) {
        return new LifeAreaResponse(
                lifeArea.getLifeId(),
                lifeArea.getCategory().name(),
                lifeArea.getRawText(),
                lifeArea.getReviewedAt() != null
        );
    }
}
