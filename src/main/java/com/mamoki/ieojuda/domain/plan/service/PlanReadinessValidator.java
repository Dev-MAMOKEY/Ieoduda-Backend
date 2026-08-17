package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.confirmer.repository.DisputeContactRepository;
import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanStatus;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 사망 사건(ReleaseCase) 생성 직전에 계획이 실제로 준비된 상태인지 재검증한다.
// PlanPackageService.seal()의 봉인 시점 검사와는 별개다 - 봉인 이후에도 확인자 거절, 항목 추가 등으로
// 계획 상태가 바뀔 수 있으므로 사건 생성 시점에 독립적으로 다시 확인해야 한다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanReadinessValidator {

    private final ConfirmerRepository confirmerRepository;
    private final ItemRepository itemRepository;
    private final DisputeContactRepository disputeContactRepository;

    public void validate(Plan plan) {
        if (plan.getStatus() != PlanStatus.SEALED) {
            throw new CustomException(ErrorCode.PLAN_NOT_READY);
        }
        if (plan.getWaitingDays() == null) {
            throw new CustomException(ErrorCode.WAITING_PERIOD_NOT_SET);
        }
        if (!Boolean.TRUE.equals(plan.getSelfWarningEmailVerified())) {
            throw new CustomException(ErrorCode.SELF_WARNING_EMAIL_NOT_VERIFIED);
        }

        boolean hasVerifiedDisputeContact = disputeContactRepository
                .findFirstByPlan_PlanIdOrderByContactIdDesc(plan.getPlanId())
                .filter(contact -> Boolean.TRUE.equals(contact.getIsVerified()))
                .isPresent();
        if (!hasVerifiedDisputeContact) {
            throw new CustomException(ErrorCode.DISPUTE_CONTACT_NOT_VERIFIED);
        }

        List<Item> items = itemRepository.findByLifeArea_Plan_PlanIdOrderBySortOrderAscItemIdAsc(plan.getPlanId());
        boolean hasUnassignedItem = items.stream().anyMatch(item -> item.getRecipient() == null);
        if (hasUnassignedItem) {
            throw new CustomException(ErrorCode.ITEM_ASSIGNEE_MISSING);
        }

        if (plan.getOrderConfirmedAt() == null) {
            throw new CustomException(ErrorCode.ORDER_NOT_CONFIRMED);
        }

        long distinctAcceptedConfirmers = confirmerRepository.findByPlan_PlanIdOrderByConfirmIdAsc(plan.getPlanId()).stream()
                .filter(confirmer -> confirmer.getAcceptanceStatus() == AcceptanceStatus.ACCEPTED)
                .map(confirmer -> confirmer.getEmail().trim().toLowerCase())
                .distinct()
                .count();
        if (distinctAcceptedConfirmers < 2) {
            throw new CustomException(ErrorCode.INSUFFICIENT_CONFIRMERS);
        }
    }
}
