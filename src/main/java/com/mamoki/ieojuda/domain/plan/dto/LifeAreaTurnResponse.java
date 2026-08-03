package com.mamoki.ieojuda.domain.plan.dto;

import com.mamoki.ieojuda.domain.plan.entity.Item;

import java.util.List;

// 사용자 발화 전송(POST) 한 번에 대한 응답 - AI가 되묻는 중인지, 구조화를 끝냈는지에 따라 내용이 달라짐
public record LifeAreaTurnResponse(
        String type,              // "QUESTION" 또는 "RESULT"
        String question,          // type=QUESTION일 때만 값 존재
        List<ItemResponse> items  // type=RESULT일 때만 값 존재 (승인/수정/기각 검토 화면에서 그대로 사용)
) {
    public record ItemResponse(
            Long itemId,
            String locationType,
            String action,
            String precondition,
            String disclosureScope,
            String sourceExcerpt,
            String status
    ) {
        public static ItemResponse from(Item item) {
            return new ItemResponse(
                    item.getItemId(),
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
