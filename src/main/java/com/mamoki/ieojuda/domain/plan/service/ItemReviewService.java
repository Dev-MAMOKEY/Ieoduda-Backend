package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.plan.dto.ItemReviewDecision;
import com.mamoki.ieojuda.domain.plan.dto.ItemReviewRequest;
import com.mamoki.ieojuda.domain.plan.dto.LifeAreaTurnResponse;
import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 명세서 "AI 구조화 결과 검토" 화면 - AI가 만든 항목을 사용자가 직접 승인/기각
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemReviewService {

    private final ItemRepository itemRepository;

    @Transactional
    public LifeAreaTurnResponse.ItemResponse review(Long planId, ItemReviewRequest request) {
        Item item = findItem(planId, request.itemId());

        if (request.decision() == ItemReviewDecision.APPROVE) {
            // 명세서 예외 처리: 원문 근거 없는 항목은 승인할 수 없음
            if (item.getSourceExcerpt() == null || item.getSourceExcerpt().isBlank()) {
                throw new CustomException(ErrorCode.UNGROUNDED_ITEM_NOT_APPROVABLE);
            }
            item.approve();
        } else {
            item.reject();
        }

        return LifeAreaTurnResponse.ItemResponse.from(item);
    }

    private Item findItem(Long planId, Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));
        if (!item.getLifeArea().getPlan().getPlanId().equals(planId)) {
            throw new CustomException(ErrorCode.ITEM_NOT_FOUND);
        }
        return item;
    }
}
