package com.mamoki.ieojuda.domain.plan.dto;

// AI 응답의 "items" 배열 원소 하나 (Item 엔티티로 그대로 옮겨 담기는 형태)
public record AiStructuredItemDto(
        String targetName,      // 이 항목의 대상 이름 (예: 아내, 김민수)
        String locationType,
        String action,
        String precondition,
        String disclosureScope, // DisclosureScope.name()과 동일한 문자열(FAMILY/WORK/RELATIONSHIP)이어야 함
        String sourceExcerpt    // 원문 근거 또는 선택값 근거
) {
}
