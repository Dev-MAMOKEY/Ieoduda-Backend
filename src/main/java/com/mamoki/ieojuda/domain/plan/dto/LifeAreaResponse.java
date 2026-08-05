package com.mamoki.ieojuda.domain.plan.dto;

import com.mamoki.ieojuda.domain.plan.entity.LifeArea;

public record LifeAreaResponse(
        Long lifeAreaId,
        String category,
        String rawText,
        boolean reviewed
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
