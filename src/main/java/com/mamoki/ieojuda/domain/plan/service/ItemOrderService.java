package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.plan.dto.ItemReorderRequest;
import com.mamoki.ieojuda.domain.plan.dto.OrderCheckItemResponse;
import com.mamoki.ieojuda.domain.plan.dto.OrderCheckResponse;
import com.mamoki.ieojuda.domain.plan.dto.OrderConfirmResponse;
import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.plan.entity.ItemSemanticType;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// 명세서 "실행 순서 점검" 화면 - 담당자가 배정된 항목들을 사용자가 드래그로 재정렬하고,
// 삭제형 항목이 인계형 항목보다 먼저 오는 순서 충돌이 없을 때만 확정할 수 있다.
// issue #90 - 명세서 "AI 구조화 및 충돌 정책"의 결정론적 규칙 6종을 전부 이 화면의 충돌 카드로 노출한다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemOrderService {

    // issue #90 (제안) - 한 담당자가 전체 항목의 과반(50% 초과)을 맡으면 권한 집중으로 경고
    private static final double AUTHORITY_CONCENTRATION_THRESHOLD = 0.5;

    private final PlanOwnershipReader planOwnershipReader;
    private final ItemRepository itemRepository;
    private final RecipientRepository recipientRepository;

    public OrderCheckResponse getOrderCheck(UUID userId, UUID planId) {
        planOwnershipReader.findOwnedPlan(userId, planId);
        List<Item> items = itemRepository.findByLifeArea_Plan_PlanIdOrderBySortOrderAscItemIdAsc(planId);
        return OrderCheckResponse.of(buildItemResponses(planId, items));
    }

    // 드래그로 바뀐 순서를 저장 - 순서가 바뀌었으므로 기존 확정 상태는 초기화한다
    @Transactional
    public OrderCheckResponse reorder(UUID userId, UUID planId, ItemReorderRequest request) {
        Plan plan = planOwnershipReader.findOwnedPlan(userId, planId);
        List<Item> items = itemRepository.findByLifeArea_Plan_PlanIdOrderBySortOrderAscItemIdAsc(planId);

        Map<UUID, Item> itemsById = items.stream().collect(Collectors.toMap(Item::getItemId, item -> item));
        Set<UUID> requestedIds = new HashSet<>(request.itemIds());
        // 순서 점검 대상 전체가 빠짐없이, 중복 없이 와야 한다 - 일부만 오면 나머지 항목의 순서가 불명확해짐
        if (requestedIds.size() != request.itemIds().size() || !requestedIds.equals(itemsById.keySet())) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        for (int i = 0; i < request.itemIds().size(); i++) {
            itemsById.get(request.itemIds().get(i)).updateSortOrder(i);
        }
        plan.resetOrderConfirmation();

        List<Item> reordered = itemRepository.findByLifeArea_Plan_PlanIdOrderBySortOrderAscItemIdAsc(planId);
        return OrderCheckResponse.of(buildItemResponses(planId, reordered));
    }

    // "순서 확정하기" - 충돌이 하나라도 남아있으면 확정할 수 없다
    @Transactional
    public OrderConfirmResponse confirm(UUID userId, UUID planId) {
        Plan plan = planOwnershipReader.findOwnedPlan(userId, planId);
        List<Item> items = itemRepository.findByLifeArea_Plan_PlanIdOrderBySortOrderAscItemIdAsc(planId);

        boolean hasConflict = buildItemResponses(planId, items).stream().anyMatch(OrderCheckItemResponse::conflict);
        if (hasConflict) {
            throw new CustomException(ErrorCode.ITEM_ORDER_CONFLICT);
        }

        plan.confirmOrder();
        return OrderConfirmResponse.from(plan);
    }

    // issue #90 - 명세서 "AI 구조화 및 충돌 정책"의 순서 쌍 규칙 3종. semanticType이 이 쌍에 해당하고
    // 같은 locationType(같은 계정·채널·기기)을 공유하는 항목끼리만 비교한다 - locationType이 다르면
    // 서로 무관한 대상이므로 타입만 같다고 엉뚱하게 충돌로 묶이지 않는다.
    private static final List<SemanticPairRule> SEMANTIC_PAIR_RULES = List.of(
            new SemanticPairRule(ItemSemanticType.CLOUD_DELETE, ItemSemanticType.FILE_PRESERVE,
                    "%s을(를) 먼저 정리하면 %s을(를) 인계할 수 없어요. %s을(를) 나중으로 실행하는 걸 추천해요.",
                    "%s이(가) 먼저 정리되면 이 항목을 인계할 수 없어요. %s을(를) 나중으로 실행하는 걸 추천해요."),
            new SemanticPairRule(ItemSemanticType.CHANNEL_CLOSE, ItemSemanticType.CLIENT_NOTIFY,
                    "%s을(를) 먼저 폐쇄하면 %s을(를) 통지할 수 없어요. %s을(를) 나중으로 실행하는 걸 추천해요.",
                    "%s이(가) 먼저 폐쇄되면 이 항목으로 통지할 수 없어요. %s을(를) 나중으로 실행하는 걸 추천해요."),
            new SemanticPairRule(ItemSemanticType.DEVICE_RESET, ItemSemanticType.RECORD_EXPORT,
                    "%s을(를) 먼저 초기화하면 %s을(를) 반출할 수 없어요. %s을(를) 나중으로 실행하는 걸 추천해요.",
                    "%s이(가) 먼저 초기화되면 이 항목을 반출할 수 없어요. %s을(를) 나중으로 실행하는 걸 추천해요.")
    );

    private record SemanticPairRule(ItemSemanticType earlier, ItemSemanticType later,
                                     String earlierMessageTemplate, String laterMessageTemplate) {
    }

    private List<OrderCheckItemResponse> buildItemResponses(UUID planId, List<Item> items) {
        Map<UUID, List<String>> conflictReasons = new HashMap<>();
        checkSemanticPairRules(items, conflictReasons);
        checkMissingRecipient(items, conflictReasons);
        checkMissingBackup(planId, items, conflictReasons);
        checkAuthorityConcentration(items, conflictReasons);

        return items.stream().map(item -> {
            List<String> reasons = conflictReasons.get(item.getItemId());
            String message = reasons == null ? null : String.join(" ", reasons);
            Recipient recipient = item.getRecipient();
            return new OrderCheckItemResponse(
                    item.getItemId(),
                    item.getSortOrder(),
                    item.getTitle(),
                    item.getActionType() == null ? null : item.getActionType().name(),
                    item.getSemanticType() == null ? null : item.getSemanticType().name(),
                    recipient == null ? null : recipient.getName(),
                    recipient == null ? null : recipient.getMaxWaitHours(),
                    recipient == null ? null : recipient.getAcceptanceStatus().name(),
                    message != null,
                    message
            );
        }).toList();
    }

    // 규칙 1~3 - 같은 locationType(같은 계정·채널·기기) 안에서만 순서 쌍을 비교한다 (담당자 유무와 무관)
    private void checkSemanticPairRules(List<Item> items, Map<UUID, List<String>> conflictReasons) {
        Map<String, List<Item>> byLocation = items.stream()
                .filter(item -> item.getLocationType() != null && !item.getLocationType().isBlank())
                .collect(Collectors.groupingBy(Item::getLocationType));

        for (List<Item> group : byLocation.values()) {
            if (group.size() < 2) {
                continue;
            }
            for (SemanticPairRule rule : SEMANTIC_PAIR_RULES) {
                List<Item> earlierItems = group.stream().filter(item -> item.getSemanticType() == rule.earlier()).toList();
                List<Item> laterItems = group.stream().filter(item -> item.getSemanticType() == rule.later()).toList();

                for (Item earlierItem : earlierItems) {
                    for (Item laterItem : laterItems) {
                        if (sortOrderOf(earlierItem) <= sortOrderOf(laterItem)) {
                            addReason(conflictReasons, earlierItem.getItemId(), String.format(
                                    rule.earlierMessageTemplate(), earlierItem.getLocationType(), laterItem.getTitle(), earlierItem.getTitle()));
                            addReason(conflictReasons, laterItem.getItemId(), String.format(
                                    rule.laterMessageTemplate(), earlierItem.getTitle(), earlierItem.getTitle()));
                        }
                    }
                }
            }
        }
    }

    // 규칙 4 - 담당자 누락
    private void checkMissingRecipient(List<Item> items, Map<UUID, List<String>> conflictReasons) {
        for (Item item : items) {
            if (item.getRecipient() == null) {
                addReason(conflictReasons, item.getItemId(), "담당자가 배정되지 않았어요. 담당자를 지정해 주세요.");
            }
        }
    }

    // 규칙 6 - 대체 담당자 부재. 대체 담당자 자신은 대상에서 제외한다(대체 담당자에게 또 대체 담당자가 필요한 건 아님)
    private void checkMissingBackup(UUID planId, List<Item> items, Map<UUID, List<String>> conflictReasons) {
        List<Recipient> planRecipients = recipientRepository.findByPlan_PlanId(planId);
        Set<UUID> primaryIdsWithBackup = planRecipients.stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsBackup()) && r.getBackupFor() != null)
                .map(r -> r.getBackupFor().getAssigneeId())
                .collect(Collectors.toSet());

        for (Item item : items) {
            Recipient recipient = item.getRecipient();
            if (recipient == null || Boolean.TRUE.equals(recipient.getIsBackup())) {
                continue;
            }
            if (!primaryIdsWithBackup.contains(recipient.getAssigneeId())) {
                addReason(conflictReasons, item.getItemId(), String.format(
                        "%s의 대체 담당자가 없어요. 무응답 시 다음 단계가 막힐 수 있어요. 대체 담당자를 등록해 주세요.", recipient.getName()));
            }
        }
    }

    // 규칙 5 - 권한 집중. 전체 항목(담당자 미배정 포함) 대비 한 담당자의 배정 비율이 기준을 넘으면 그 담당자의 항목 전부를 표시.
    // 담당자가 한 명뿐이면 재분배할 대상이 없으므로 검사하지 않는다.
    private void checkAuthorityConcentration(List<Item> items, Map<UUID, List<String>> conflictReasons) {
        if (items.isEmpty()) {
            return;
        }
        Map<UUID, List<Item>> byRecipient = items.stream()
                .filter(item -> item.getRecipient() != null)
                .collect(Collectors.groupingBy(item -> item.getRecipient().getAssigneeId()));
        if (byRecipient.size() < 2) {
            return;
        }

        for (List<Item> assignedItems : byRecipient.values()) {
            if ((double) assignedItems.size() / items.size() > AUTHORITY_CONCENTRATION_THRESHOLD) {
                String recipientName = assignedItems.get(0).getRecipient().getName();
                for (Item item : assignedItems) {
                    addReason(conflictReasons, item.getItemId(), String.format(
                            "%s에게 전체 항목의 절반이 넘게 몰려 있어요. 다른 담당자에게 일부를 나눠보는 걸 추천해요.", recipientName));
                }
            }
        }
    }

    private void addReason(Map<UUID, List<String>> conflictReasons, UUID itemId, String reason) {
        conflictReasons.computeIfAbsent(itemId, key -> new ArrayList<>()).add(reason);
    }

    private int sortOrderOf(Item item) {
        return item.getSortOrder() == null ? 0 : item.getSortOrder();
    }
}
