package com.mamoki.ieojuda.domain.plan.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

// "실행 순서 점검" 화면 - 카드 하나 (담당자/대기기간/승인여부 + 순서 충돌 여부)
public record OrderCheckItemResponse(
        @Schema(description = "항목 ID") UUID itemId,
        @Schema(description = "실행 순서 (낮을수록 먼저, 화면 표시 번호는 이 순서 기준 1부터)") Integer sortOrder,
        @Schema(description = "짧은 제목") String title,
        @Schema(description = "실행 순서 충돌 판정용 분류", example = "DELETE", allowableValues = {"DELETE", "TRANSFER", "OTHER"}) String actionType,
        @Schema(description = "순서 쌍 충돌 판정용 세부 분류(해당 없으면 null)", example = "CLOUD_DELETE",
                allowableValues = {"CLOUD_DELETE", "FILE_PRESERVE", "CHANNEL_CLOSE", "CLIENT_NOTIFY", "DEVICE_RESET", "RECORD_EXPORT"}) String semanticType,
        @Schema(description = "담당자 이름") String recipientName,
        @Schema(description = "대기 기간(시간 단위)") Integer maxWaitHours,
        @Schema(description = "담당자 수락 상태", example = "PENDING", allowableValues = {"PENDING", "ACCEPTED", "DECLINED", "EXPIRED"}) String acceptanceStatus,
        @Schema(description = "이 항목이 순서 충돌에 걸려 있는지 여부") boolean conflict,
        @Schema(description = "충돌 사유 (충돌 없으면 null)") String conflictMessage
) {
}
