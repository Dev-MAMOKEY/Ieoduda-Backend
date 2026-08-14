package com.mamoki.ieojuda.domain.handoffcheck.service;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckAssigneeResponse;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckConfirmerResponse;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckStatusResponse;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// "선택형 생전 인계 점검" 화면 - 역할 담당자와 지정 확인자의 준비 상태를 함께 조회
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HandoffCheckService {

    private final PlanRepository planRepository;
    private final RecipientRepository recipientRepository;
    private final ConfirmerRepository confirmerRepository;

    public HandoffCheckStatusResponse getHandoffCheck(Long userId, Long planId) {
        findOwnedPlan(userId, planId);

        List<HandoffCheckAssigneeResponse> assignees = buildAssignees(planId);
        List<HandoffCheckConfirmerResponse> confirmers = confirmerRepository.findByPlan_PlanIdOrderByConfirmIdAsc(planId).stream()
                .map(HandoffCheckConfirmerResponse::from)
                .toList();

        return HandoffCheckStatusResponse.of(assignees, confirmers);
    }

    // 대체 담당자는 별도 박스가 아니라 주 담당자 박스 안에 표시되므로, 대체 담당자를 주 담당자 ID로 매핑해 함께 조회한다 (N+1 방지)
    private List<HandoffCheckAssigneeResponse> buildAssignees(Long planId) {
        List<Recipient> recipients = recipientRepository.findByPlan_PlanId(planId);

        Map<Long, Recipient> backupByPrimaryId = new HashMap<>();
        for (Recipient recipient : recipients) {
            if (Boolean.TRUE.equals(recipient.getIsBackup()) && recipient.getBackupFor() != null) {
                backupByPrimaryId.put(recipient.getBackupFor().getAssigneeId(), recipient);
            }
        }

        return recipients.stream()
                .filter(recipient -> !Boolean.TRUE.equals(recipient.getIsBackup()))
                .sorted(Comparator.comparing(Recipient::getAssigneeId))
                .map(recipient -> HandoffCheckAssigneeResponse.of(recipient, backupByPrimaryId.get(recipient.getAssigneeId())))
                .toList();
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
