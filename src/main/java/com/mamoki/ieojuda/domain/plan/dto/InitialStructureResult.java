package com.mamoki.ieojuda.domain.plan.dto;

import java.util.List;

// OpenAIClient.getInitialStructure()가 돌려준 JSON(choices[0].message.content)을 파싱한 결과.
// 계획 생성은 1회성 제출이라 AiTurnResult와 달리 QUESTION 분기 없이 items만 있음.
public record InitialStructureResult(
        List<AiStructuredItemDto> items
) {
}
