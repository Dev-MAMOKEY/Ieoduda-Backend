package com.mamoki.ieojuda.domain.plan.dto;

// AI 응답의 "items" 배열 원소 하나 (Item 엔티티로 그대로 옮겨 담기는 형태)
public record AiStructuredItemDto(
        String targetName,      // 이 항목의 대상 이름 (예: 아내, 김민수)
        String locationType,
        String action,          // 수행해야 할 행동 전체 설명 (예: "인스타그램 아이디 비밀번호를 통해 SNS 계정을 정리해줘")
        String title,           // 짧은 제목 - 대상 채널/계정명 (예: "인스타그램")
        String content,         // 그 채널에 대해 할 일 (예: "탈퇴 처리")
        String precondition,
        String disclosureScope, // DisclosureScope.name()과 동일한 문자열(FAMILY/WORK/RELATIONSHIP)이어야 함
        String sourceExcerpt,   // 원문 근거 또는 선택값 근거
        Integer sortOrder,      // 같은 응답 안 항목들끼리의 실행 순서 (낮을수록 먼저)
        String actionType       // "실행 순서 점검" 충돌 판정용 분류 - DELETE/TRANSFER/OTHER 중 하나여야 함
) {
}
