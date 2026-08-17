package com.mamoki.ieojuda.domain.handoffcheck.dto;

import com.mamoki.ieojuda.domain.handoffcheck.entity.HandoffCheckResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// "선택형 생전 인계 점검" 응답 화면 - 명세서 "노출 가능" 범위만 반환 (항목 본문·위치 유형·대상 목록은 절대 포함하지 않는다)
public record HandoffCheckRespondResponse(
        @Schema(description = "담당자 역할명") String roleType,
        @Schema(description = "공개 범주") String disclosureScope,
        @Schema(description = "작성자 이름") String authorName,
        @Schema(description = "응답 시각") LocalDateTime respondedAt
) {
    public static HandoffCheckRespondResponse from(HandoffCheckResponse response) {
        return new HandoffCheckRespondResponse(
                response.getRecipient().getRoleType().name(),
                response.getRecipient().getDisclosureScope().name(),
                response.getHandoffCheck().getPlan().getUser().getName(),
                response.getRespondedAt()
        );
    }
}
