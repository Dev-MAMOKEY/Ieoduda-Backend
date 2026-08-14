package com.mamoki.ieojuda.domain.plan.dto;

import com.mamoki.ieojuda.domain.plan.entity.Item;
import io.swagger.v3.oas.annotations.media.Schema;

// AI가 만든(또는 사용자가 검토한) 항목 하나 - 대화 턴 응답, 구역별 목록, 검토/수정 응답에서 공통으로 사용
public record ItemResponse(
        @Schema(description = "항목 ID") Long itemId,
        @Schema(description = "이 항목의 대상 이름", example = "김민수") String targetName,
        @Schema(description = "자료/계정 위치 유형", example = "구글 클라우드 앨범") String locationType,
        @Schema(description = "수행해야 할 행동 전체 설명", example = "인스타그램 아이디 비밀번호를 통해 SNS 계정을 정리해줘") String action,
        @Schema(description = "짧은 제목 - 대상 채널/계정명", example = "인스타그램") String title,
        @Schema(description = "그 채널에 대해 할 일", example = "탈퇴 처리") String content,
        @Schema(description = "선행 조건 (없으면 빈 문자열)") String precondition,
        @Schema(description = "공개 범위", example = "FAMILY", allowableValues = {"FAMILY", "WORK", "RELATIONSHIP"}) String disclosureScope,
        @Schema(description = "이 항목의 근거가 되는 원문 문장") String sourceExcerpt,
        @Schema(description = "검토 상태", example = "PROPOSED", allowableValues = {"PROPOSED", "APPROVED"}) String status,
        @Schema(description = "같은 대화 턴에서 만들어진 항목들끼리의 실행 순서 (낮을수록 먼저)") Integer sortOrder,
        @Schema(description = "실행 순서 충돌 판정용 분류", example = "DELETE", allowableValues = {"DELETE", "TRANSFER", "OTHER"}) String actionType
) {
    public static ItemResponse from(Item item) {
        return new ItemResponse(
                item.getItemId(),
                item.getTargetName(),
                item.getLocationType(),
                item.getAction(),
                item.getTitle(),
                item.getContent(),
                item.getPrecondition(),
                item.getDisclosureScope() == null ? null : item.getDisclosureScope().name(),
                item.getSourceExcerpt(),
                item.getStatus().name(),
                item.getSortOrder(),
                item.getActionType() == null ? null : item.getActionType().name()
        );
    }
}
