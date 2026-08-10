package com.mamoki.ieojuda.domain.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// 명세서 "AI 구조화 결과 검토" 화면 - GET /api/plans/{planId}/compilations/{lifeAreaId}
// 새 엔티티 없이 LifeArea.aiStructuredResult(구역별 최신 AI 구조화 원문)를 재조회한다.
// 대화가 계속되어 다시 구조화될 때마다 덮어써지므로 항상 "최신" 결과만 볼 수 있다(이전 이력 조회는 불가).
public record CompilationResponse(
        @Schema(description = "삶의 구역 ID") Long lifeAreaId,
        @Schema(description = "구역 분류", example = "FAMILY", allowableValues = {"FAMILY", "RELATIONSHIP_CLEANUP", "WORK_CONTINUITY"}) String category,
        @Schema(description = "아직 AI 구조화가 한 번도 실행되지 않았으면 true") boolean empty,
        @Schema(description = "AI가 구조화한 항목 목록 (empty=true면 빈 목록)") List<AiStructuredItemDto> items
) {
}
