package com.mamoki.ieojuda.domain.plan.dto;

import jakarta.validation.constraints.NotBlank;

// 대화창 인라인 "수정" 버튼 - 항목 내용을 사용자가 직접 고쳐 저장
public record ItemUpdateRequest(
        @NotBlank(message = "대상 이름을 입력해 주세요.") String targetName,
        @NotBlank(message = "위치 유형을 입력해 주세요.") String locationType,
        @NotBlank(message = "행동을 입력해 주세요.") String action,
        String precondition,
        @NotBlank(message = "공개 범위를 선택해 주세요.") String disclosureScope
) {
}
