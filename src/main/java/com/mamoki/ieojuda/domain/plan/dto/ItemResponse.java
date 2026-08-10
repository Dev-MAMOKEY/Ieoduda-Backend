package com.mamoki.ieojuda.domain.plan.dto;

import com.mamoki.ieojuda.domain.plan.entity.Item;
import io.swagger.v3.oas.annotations.media.Schema;

// AI가 만든(또는 사용자가 검토한) 항목 하나 - 대화 턴 응답, 구역별 목록, 검토/수정 응답에서 공통으로 사용
public record ItemResponse(
        @Schema(description = "항목 ID") Long itemId,
        @Schema(description = "이 항목의 대상 이름", example = "김민수") String targetName,
        @Schema(description = "자료/계정 위치 유형", example = "구글 클라우드 앨범") String locationType,
        @Schema(description = "수행해야 할 행동", example = "가족사진 위치를 공유") String action,
        @Schema(description = "선행 조건 (없으면 빈 문자열)") String precondition,
        @Schema(description = "공개 범위", example = "FAMILY", allowableValues = {"FAMILY", "WORK", "RELATIONSHIP"}) String disclosureScope,
        @Schema(description = "이 항목의 근거가 되는 원문 문장") String sourceExcerpt,
        @Schema(description = "검토 상태", example = "PROPOSED", allowableValues = {"PROPOSED", "APPROVED", "REJECTED"}) String status
) {
    public static ItemResponse from(Item item) {
        return new ItemResponse(
                item.getItemId(),
                item.getTargetName(),
                item.getLocationType(),
                item.getAction(),
                item.getPrecondition(),
                item.getDisclosureScope() == null ? null : item.getDisclosureScope().name(),
                item.getSourceExcerpt(),
                item.getStatus().name()
        );
    }
}
