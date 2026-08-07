package com.mamoki.ieojuda.domain.plan.dto;

import com.mamoki.ieojuda.domain.plan.entity.Item;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// 사용자 발화 전송(POST) 한 번에 대한 응답 - AI가 되묻는 중인지, 구조화를 끝냈는지에 따라 내용이 달라짐
public record LifeAreaTurnResponse(
        @Schema(description = "AI 응답 종류", example = "QUESTION", allowableValues = {"QUESTION", "RESULT"}) String type,
        @Schema(description = "type=QUESTION일 때만 값 존재 - AI가 되묻는 질문") String question,
        @Schema(description = "type=RESULT일 때만 값 존재 - AI가 구조화한 항목 목록") List<ItemResponse> items
) {
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
}
