package com.mamoki.ieojuda.domain.handoffcheck.service;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckAssigneeResponse;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckConfirmerResponse;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckStatusResponse;
import com.mamoki.ieojuda.domain.plan.service.PlanOwnershipReader;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.UUID;
import java.util.List;
import java.util.Map;

// "선택형 생전 인계 점검" 화면 - 역할 담당자와 지정 확인자의 준비 상태를 함께 조회
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HandoffCheckService {

    private final PlanOwnershipReader planOwnershipReader;
    private final RecipientRepository recipientRepository;
    private final ConfirmerRepository confirmerRepository;

    public HandoffCheckStatusResponse getHandoffCheck(UUID userId, UUID planId) {
        planOwnershipReader.findOwnedPlan(userId, planId);

        List<HandoffCheckAssigneeResponse> assignees = buildAssignees(planId);
        List<HandoffCheckConfirmerResponse> confirmers = confirmerRepository.findByPlan_PlanIdOrderByConfirmIdAsc(planId).stream()
                .map(HandoffCheckConfirmerResponse::from)
                .toList();

        return HandoffCheckStatusResponse.of(assignees, confirmers);
    }

    // 대체 담당자는 별도 박스가 아니라 주 담당자 박스 안에 표시되므로, 대체 담당자를 주 담당자 ID로 매핑해 함께 조회한다 (N+1 방지)
    private List<HandoffCheckAssigneeResponse> buildAssignees(UUID planId) {
        List<Recipient> recipients = recipientRepository.findByPlan_PlanId(planId);

        Map<UUID, Recipient> backupByPrimaryId = new HashMap<>();
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
}
