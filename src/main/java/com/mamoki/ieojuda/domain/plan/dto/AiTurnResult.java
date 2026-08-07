package com.mamoki.ieojuda.domain.plan.dto;

import java.util.List;

// OpenAIClient가 돌려준 JSON 문자열(choices[0].message.content)을 파싱한 결과.
// OpenAIClient의 COMPILE_POLICY_PROMPT가 강제하는 응답 스키마와 1:1로 대응한다.
public record AiTurnResult(
        String type,                     // "QUESTION" 또는 "RESULT"
        String question,                 // type=QUESTION일 때만 값 존재
        List<AiStructuredItemDto> items   // type=RESULT일 때만 값 존재
) {
}
