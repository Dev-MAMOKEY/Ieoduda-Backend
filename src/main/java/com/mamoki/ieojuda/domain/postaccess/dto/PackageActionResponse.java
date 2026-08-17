package com.mamoki.ieojuda.domain.postaccess.dto;

import com.mamoki.ieojuda.domain.plan.dto.PlanSnapshotDto;
import io.swagger.v3.oas.annotations.media.Schema;

// 명세서 "역할별 사후 패키지" - 담당자가 수행할 행동 하나. 봉인된 스냅샷의 항목 하나에 대응한다.
public record PackageActionResponse(
        @Schema(description = "행동(항목) ID") Long actionId,
        @Schema(description = "제목") String title,
        @Schema(description = "행동 내용") String content,
        @Schema(description = "대상 이름") String targetName,
        @Schema(description = "위치 유형") String locationType,
        @Schema(description = "선행 조건") String precondition,
        @Schema(description = "선행 조건 충족 여부") boolean preconditionMet,
        @Schema(description = "완료 상태", allowableValues = {"PENDING", "COMPLETED"}) String status
) {
    public static PackageActionResponse of(PlanSnapshotDto.ItemSnapshot item, boolean completed) {
        boolean preconditionMet = item.precondition() == null || item.precondition().isBlank();
        return new PackageActionResponse(
                item.itemId(),
                item.title(),
                item.content(),
                item.targetName(),
                item.locationType(),
                item.precondition(),
                preconditionMet,
                completed ? "COMPLETED" : "PENDING"
        );
    }
}
