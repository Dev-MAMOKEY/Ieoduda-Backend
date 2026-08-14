package com.mamoki.ieojuda.domain.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// "실행 순서 점검" 화면 전체 응답
public record OrderCheckResponse(
        @Schema(description = "실행 순서대로 정렬된 항목 목록") List<OrderCheckItemResponse> items,
        @Schema(description = "하나라도 충돌이 있으면 true - true인 동안은 순서를 확정할 수 없음") boolean hasConflict
) {
    public static OrderCheckResponse of(List<OrderCheckItemResponse> items) {
        boolean hasConflict = items.stream().anyMatch(OrderCheckItemResponse::conflict);
        return new OrderCheckResponse(items, hasConflict);
    }
}
