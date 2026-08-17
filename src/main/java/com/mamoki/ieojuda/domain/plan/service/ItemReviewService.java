package com.mamoki.ieojuda.domain.plan.service;

import java.util.UUID;

import com.mamoki.ieojuda.domain.plan.dto.ItemReviewRequest;
import com.mamoki.ieojuda.domain.plan.dto.ItemResponse;
import com.mamoki.ieojuda.domain.plan.dto.ItemUpdateRequest;
import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.plan.entity.ItemActionType;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.plan.entity.ItemStatus;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.validation.CredentialDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 명세서 "AI 구조화 결과 검토" 화면 - AI가 만든 항목을 사용자가 직접 승인/수정/삭제
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemReviewService {

    private final ItemRepository itemRepository;
    private final PlanOwnershipReader planOwnershipReader;

    // 항목 단건 조회
    public ItemResponse getItem(UUID userId, UUID planId, UUID itemId) {
        return ItemResponse.from(findItem(userId, planId, itemId));
    }

    // issue #86 - 항목 목록 조회. status/disclosureScope 필터는 선택적, sortOrder 기준 정렬
    public List<ItemResponse> getItems(UUID userId, UUID planId, ItemStatus status, DisclosureScope disclosureScope) {
        planOwnershipReader.findOwnedPlan(userId, planId);
        return itemRepository.findByPlanIdAndFilters(planId, status, disclosureScope).stream()
                .map(ItemResponse::from)
                .toList();
    }

    @Transactional
    public ItemResponse approve(UUID userId, UUID planId, ItemReviewRequest request) {
        Item item = findItem(userId, planId, request.itemId());

        // 명세서 예외 처리: 원문 근거 없는 항목은 승인할 수 없음
        if (item.getSourceExcerpt() == null || item.getSourceExcerpt().isBlank()) {
            throw new CustomException(ErrorCode.UNGROUNDED_ITEM_NOT_APPROVABLE);
        }
        // issue #91 3차 방어 - 대화 저장 시점 검증을 우회해 들어온(예: 인라인 수정) 자격증명도 승인 시점에 다시 막는다
        if (CredentialDetector.containsCredential(item.getAction())
                || CredentialDetector.containsCredential(item.getContent())
                || CredentialDetector.containsCredential(item.getLocationType())) {
            throw new CustomException(ErrorCode.SUSPECTED_CREDENTIAL_INPUT);
        }
        item.approve();

        return ItemResponse.from(item);
    }

    // "삭제" 버튼 - 기각(상태만 변경) 대신 항목을 DB에서 완전히 제거
    @Transactional
    public void delete(UUID userId, UUID planId, UUID itemId) {
        Item item = findItem(userId, planId, itemId);
        itemRepository.delete(item);
    }

    // 대화창 인라인 "수정" 버튼 - AI가 만든 초안을 사용자가 직접 고쳐서 저장 (대화 화면 전환 없이 처리)
    @Transactional
    public ItemResponse update(UUID userId, UUID planId, UUID itemId, ItemUpdateRequest request) {
        Item item = findItem(userId, planId, itemId);

        DisclosureScope disclosureScope;
        try {
            disclosureScope = DisclosureScope.valueOf(request.disclosureScope());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        ItemActionType actionType;
        try {
            actionType = request.actionType() == null ? ItemActionType.OTHER : ItemActionType.valueOf(request.actionType());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        item.updateContent(request.targetName(), request.locationType(), request.action(), request.title(), request.content(),
                request.precondition(), disclosureScope, actionType);

        return ItemResponse.from(item);
    }

    private Item findItem(UUID userId, UUID planId, UUID itemId) {
        planOwnershipReader.findOwnedPlan(userId, planId);
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));
        if (!item.getLifeArea().getPlan().getPlanId().equals(planId)) {
            throw new CustomException(ErrorCode.ITEM_NOT_FOUND);
        }
        return item;
    }
}
