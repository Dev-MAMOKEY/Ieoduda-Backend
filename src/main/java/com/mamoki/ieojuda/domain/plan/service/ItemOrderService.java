package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.plan.dto.ItemReorderRequest;
import com.mamoki.ieojuda.domain.plan.dto.OrderCheckItemResponse;
import com.mamoki.ieojuda.domain.plan.dto.OrderCheckResponse;
import com.mamoki.ieojuda.domain.plan.dto.OrderConfirmResponse;
import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.plan.entity.ItemActionType;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// 명세서 "실행 순서 점검" 화면 - 담당자가 배정된 항목들을 사용자가 드래그로 재정렬하고,
// 삭제형 항목이 인계형 항목보다 먼저 오는 순서 충돌이 없을 때만 확정할 수 있다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemOrderService {

    private final PlanRepository planRepository;
    private final ItemRepository itemRepository;

    public OrderCheckResponse getOrderCheck(Long userId, Long planId) {
        findOwnedPlan(userId, planId);
        List<Item> items = itemRepository.findByLifeArea_Plan_PlanIdAndRecipientIsNotNullOrderBySortOrderAscItemIdAsc(planId);
        return OrderCheckResponse.of(buildItemResponses(items));
    }

    // 드래그로 바뀐 순서를 저장 - 순서가 바뀌었으므로 기존 확정 상태는 초기화한다
    @Transactional
    public OrderCheckResponse reorder(Long userId, Long planId, ItemReorderRequest request) {
        Plan plan = findOwnedPlan(userId, planId);
        List<Item> items = itemRepository.findByLifeArea_Plan_PlanIdAndRecipientIsNotNullOrderBySortOrderAscItemIdAsc(planId);

        Map<Long, Item> itemsById = items.stream().collect(Collectors.toMap(Item::getItemId, item -> item));
        Set<Long> requestedIds = new HashSet<>(request.itemIds());
        // 순서 점검 대상 전체가 빠짐없이, 중복 없이 와야 한다 - 일부만 오면 나머지 항목의 순서가 불명확해짐
        if (requestedIds.size() != request.itemIds().size() || !requestedIds.equals(itemsById.keySet())) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        for (int i = 0; i < request.itemIds().size(); i++) {
            itemsById.get(request.itemIds().get(i)).updateSortOrder(i);
        }
        plan.resetOrderConfirmation();

        List<Item> reordered = itemRepository.findByLifeArea_Plan_PlanIdAndRecipientIsNotNullOrderBySortOrderAscItemIdAsc(planId);
        return OrderCheckResponse.of(buildItemResponses(reordered));
    }

    // "순서 확정하기" - 충돌이 하나라도 남아있으면 확정할 수 없다
    @Transactional
    public OrderConfirmResponse confirm(Long userId, Long planId) {
        Plan plan = findOwnedPlan(userId, planId);
        List<Item> items = itemRepository.findByLifeArea_Plan_PlanIdAndRecipientIsNotNullOrderBySortOrderAscItemIdAsc(planId);

        boolean hasConflict = buildItemResponses(items).stream().anyMatch(OrderCheckItemResponse::conflict);
        if (hasConflict) {
            throw new CustomException(ErrorCode.ITEM_ORDER_CONFLICT);
        }

        plan.confirmOrder();
        return OrderConfirmResponse.from(plan);
    }

    // 같은 locationType(자료 위치) 안에서 DELETE형 항목이 TRANSFER형 항목보다 먼저(또는 같이) 오면 충돌로 판정한다
    private List<OrderCheckItemResponse> buildItemResponses(List<Item> items) {
        Map<String, List<Item>> byLocation = items.stream()
                .filter(item -> item.getLocationType() != null && !item.getLocationType().isBlank())
                .collect(Collectors.groupingBy(Item::getLocationType));

        Map<Long, String> conflictMessages = new HashMap<>();
        for (List<Item> group : byLocation.values()) {
            if (group.size() < 2) {
                continue;
            }
            List<Item> deletes = group.stream().filter(item -> item.getActionType() == ItemActionType.DELETE).toList();
            List<Item> transfers = group.stream().filter(item -> item.getActionType() == ItemActionType.TRANSFER).toList();

            for (Item deleteItem : deletes) {
                for (Item transferItem : transfers) {
                    if (sortOrderOf(deleteItem) <= sortOrderOf(transferItem)) {
                        conflictMessages.put(deleteItem.getItemId(), String.format(
                                "%s을(를) 먼저 정리하면 %s을(를) 인계할 수 없어요. %s을(를) 나중으로 실행하는 걸 추천해요.",
                                deleteItem.getLocationType(), transferItem.getTitle(), deleteItem.getTitle()));
                        conflictMessages.put(transferItem.getItemId(), String.format(
                                "%s이(가) 먼저 정리되면 이 항목을 인계할 수 없어요. %s을(를) 나중으로 실행하는 걸 추천해요.",
                                deleteItem.getTitle(), deleteItem.getTitle()));
                    }
                }
            }
        }

        return items.stream().map(item -> {
            String message = conflictMessages.get(item.getItemId());
            Recipient recipient = item.getRecipient();
            return new OrderCheckItemResponse(
                    item.getItemId(),
                    item.getSortOrder(),
                    item.getTitle(),
                    item.getActionType() == null ? null : item.getActionType().name(),
                    recipient.getName(),
                    recipient.getMaxWaitHours(),
                    recipient.getAcceptanceStatus().name(),
                    message != null,
                    message
            );
        }).toList();
    }

    private int sortOrderOf(Item item) {
        return item.getSortOrder() == null ? 0 : item.getSortOrder();
    }

    // 로그인한 사용자가 자신의 계획만 조회할 수 있도록 검증 (불일치 시 존재 노출 방지를 위해 NOT_FOUND로 응답)
    private Plan findOwnedPlan(Long userId, Long planId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAN_NOT_FOUND));
        if (!plan.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.PLAN_NOT_FOUND);
        }
        return plan;
    }
}
