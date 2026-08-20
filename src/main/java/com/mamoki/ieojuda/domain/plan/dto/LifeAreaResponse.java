package com.mamoki.ieojuda.domain.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// 카테고리(가족/관계 정리/업무 연속성)별로 지금까지 쌓인 항목 전체를 모아서 보여준다.
// 같은 카테고리라도 대화 세션(Conversation)마다 LifeArea 행이 새로 생길 수 있어서,
// 여기서는 특정 세션이 아니라 계획(Plan) 전체를 기준으로 항목을 집계한다.
public record LifeAreaResponse(
        @Schema(description = "구역 분류", example = "FAMILY", allowableValues = {"FAMILY", "RELATIONSHIP_CLEANUP", "WORK_CONTINUITY"}) String category,
        @Schema(description = "이 구역에 지금까지 쌓인 항목 전체") List<ItemResponse> items
) {
}
