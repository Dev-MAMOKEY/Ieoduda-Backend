package com.mamoki.ieojuda.domain.plan.dto;

import jakarta.validation.constraints.NotBlank;

// 대화창 인라인 "수정" 버튼 - 항목 내용을 사용자가 직접 고쳐 저장
public record ItemUpdateRequest(
        @NotBlank(message = "대상 이름을 입력해 주세요.")
        @jakarta.validation.constraints.Size(max = 100, message = "대상 이름은 100자 이하여야 합니다.") String targetName,
        @NotBlank(message = "위치 유형을 입력해 주세요.")
        @jakarta.validation.constraints.Size(max = 100, message = "위치 유형은 100자 이하여야 합니다.") String locationType,
        @NotBlank(message = "행동을 입력해 주세요.")
        @jakarta.validation.constraints.Size(max = 2000, message = "행동은 2,000자 이하여야 합니다.") String action,
        @NotBlank(message = "제목을 입력해 주세요.")
        @jakarta.validation.constraints.Size(max = 200, message = "제목은 200자 이하여야 합니다.") String title,
        @NotBlank(message = "내용을 입력해 주세요.")
        @jakarta.validation.constraints.Size(max = 2000, message = "내용은 2,000자 이하여야 합니다.") String content,
        @jakarta.validation.constraints.Size(max = 2000, message = "선행 조건은 2,000자 이하여야 합니다.") String precondition,
        @NotBlank(message = "공개 범위를 선택해 주세요.")
        @jakarta.validation.constraints.Size(max = 30, message = "공개 범위 값은 30자 이하여야 합니다.") String disclosureScope,
        @jakarta.validation.constraints.Size(max = 30, message = "행동 유형 값은 30자 이하여야 합니다.") String actionType,
        @jakarta.validation.constraints.Size(max = 30, message = "세부 분류 값은 30자 이하여야 합니다.") String semanticType
) {
}
