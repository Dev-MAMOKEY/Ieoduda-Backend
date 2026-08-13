package com.mamoki.ieojuda.domain.recipient.dto;

import com.mamoki.ieojuda.domain.plan.dto.ItemResponse;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// "역할 점검" 화면 상세 - 이름 클릭 시 해당 담당자에게 배정된 항목 전체
public record RecipientDetailResponse(
        @Schema(description = "담당자 ID") Long assigneeId,
        @Schema(description = "담당자 이름") String name,
        @Schema(description = "담당자 이메일") String email,
        @Schema(description = "역할 유형", example = "FAMILY_MANAGER", allowableValues = {"FAMILY_MANAGER", "WORK_MANAGER", "RELATIONSHIP_MANAGER"}) String roleType,
        @Schema(description = "수락 상태", example = "PENDING", allowableValues = {"PENDING", "ACCEPTED", "DECLINED", "EXPIRED"}) String acceptanceStatus,
        @Schema(description = "최대 단계 대기 시간(시간 단위)") Integer maxWaitHours,
        @Schema(description = "이 담당자에게 배정된 항목 전체") List<ItemResponse> items
) {
    public static RecipientDetailResponse of(Recipient recipient, List<ItemResponse> items) {
        return new RecipientDetailResponse(
                recipient.getAssigneeId(),
                recipient.getName(),
                recipient.getEmail(),
                recipient.getRoleType().name(),
                recipient.getAcceptanceStatus().name(),
                recipient.getMaxWaitHours(),
                items
        );
    }
}
