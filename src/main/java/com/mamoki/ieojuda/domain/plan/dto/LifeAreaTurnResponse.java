package com.mamoki.ieojuda.domain.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// 사용자 발화 전송(POST) 한 번에 대한 응답 - AI가 되묻는 중인지, 구조화를 끝냈는지에 따라 내용이 달라짐
public record LifeAreaTurnResponse(
        @Schema(description = "AI 응답 종류", example = "QUESTION", allowableValues = {"QUESTION", "RESULT"}) String type,
        @Schema(description = "type=QUESTION일 때만 값 존재 - AI가 되묻는 질문") String question,
        @Schema(description = "type=RESULT일 때만 값 존재 - AI가 구조화한 항목 목록 (여러 카테고리에 걸쳐 있을 수 있음)") List<ItemResponse> items
) {
}
