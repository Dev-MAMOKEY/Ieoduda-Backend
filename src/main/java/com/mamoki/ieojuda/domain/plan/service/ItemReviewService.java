package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.plan.dto.ItemReviewRequest;
import com.mamoki.ieojuda.domain.plan.dto.ItemResponse;
import com.mamoki.ieojuda.domain.plan.dto.ItemUpdateRequest;
import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 명세서 "AI 구조화 결과 검토" 화면 - AI가 만든 항목을 사용자가 직접 승인/수정/삭제
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemReviewService {

    private final ItemRepository itemRepository;

    @Transactional
    public ItemResponse approve(Long planId, ItemReviewRequest request) {
        Item item = findItem(planId, request.itemId());

        // 명세서 예외 처리: 원문 근거 없는 항목은 승인할 수 없음
        if (item.getSourceExcerpt() == null || item.getSourceExcerpt().isBlank()) {
            throw new CustomException(ErrorCode.UNGROUNDED_ITEM_NOT_APPROVABLE);
        }
        item.approve();

        return ItemResponse.from(item);
    }

    // "삭제" 버튼 - 기각(상태만 변경) 대신 항목을 DB에서 완전히 제거
    @Transactional
    public void delete(Long planId, Long itemId) {
        Item item = findItem(planId, itemId);
        itemRepository.delete(item);
    }

    // 대화창 인라인 "수정" 버튼 - AI가 만든 초안을 사용자가 직접 고쳐서 저장 (대화 화면 전환 없이 처리)
    @Transactional
    public ItemResponse update(Long planId, Long itemId, ItemUpdateRequest request) {
        Item item = findItem(planId, itemId);

        DisclosureScope disclosureScope;
        try {
            disclosureScope = DisclosureScope.valueOf(request.disclosureScope());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        item.updateContent(request.targetName(), request.locationType(), request.action(),
                request.precondition(), disclosureScope);

        return ItemResponse.from(item);
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
