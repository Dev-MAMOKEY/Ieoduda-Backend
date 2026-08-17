package com.mamoki.ieojuda.domain.plan.dto;

import com.mamoki.ieojuda.domain.plan.entity.Item;
import io.swagger.v3.oas.annotations.media.Schema;

// 명세서 "역할별 패키지 미리보기" - 담당자 한 명이 받게 될 행동 하나
public record PackageActionPreview(
        @Schema(description = "항목 ID") Long itemId,
        @Schema(description = "대상 이름") String targetName,
        @Schema(description = "위치 유형") String locationType,
        @Schema(description = "수행해야 할 행동 전체 설명") String action,
        @Schema(description = "제목") String title,
        @Schema(description = "행동 내용") String content,
        @Schema(description = "선행 조건") String precondition,
        @Schema(description = "원문 근거") String sourceExcerpt
) {
    public static PackageActionPreview from(Item item) {
        return new PackageActionPreview(
                item.getItemId(),
                item.getTargetName(),
                item.getLocationType(),
                item.getAction(),
                item.getTitle(),
                item.getContent(),
                item.getPrecondition(),
                item.getSourceExcerpt()
        );
    }
}
