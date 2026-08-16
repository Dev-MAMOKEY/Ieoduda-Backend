package com.mamoki.ieojuda.domain.rolecheck.service;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.plan.service.PlanOwnershipReader;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.domain.rolecheck.dto.RoleCheckSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// "역할 점검" 화면 상단 이름 목록 - 역할 담당자와 지정 확인자를 함께 조회
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleCheckService {

    private final PlanOwnershipReader planOwnershipReader;
    private final RecipientRepository recipientRepository;
    private final ConfirmerRepository confirmerRepository;

    public List<RoleCheckSummaryResponse> getRoleChecks(Long userId, Long planId) {
        planOwnershipReader.findOwnedPlan(userId, planId);

        List<RoleCheckSummaryResponse> results = new ArrayList<>();
        for (Recipient recipient : recipientRepository.findByPlan_PlanIdAndIsBackupFalseOrderByAssigneeIdAsc(planId)) {
            results.add(RoleCheckSummaryResponse.from(recipient));
        }
        for (Confirmer confirmer : confirmerRepository.findByPlan_PlanIdOrderByConfirmIdAsc(planId)) {
            results.add(RoleCheckSummaryResponse.from(confirmer));
        }
        return results;
    }
}
