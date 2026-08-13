package com.mamoki.ieojuda.domain.rolecheck.service;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.domain.rolecheck.dto.RoleCheckSummaryResponse;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
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

    private final PlanRepository planRepository;
    private final RecipientRepository recipientRepository;
    private final ConfirmerRepository confirmerRepository;

    public List<RoleCheckSummaryResponse> getRoleChecks(Long userId, Long planId) {
        findOwnedPlan(userId, planId);

        List<RoleCheckSummaryResponse> results = new ArrayList<>();
        for (Recipient recipient : recipientRepository.findByPlan_PlanIdAndIsBackupFalseOrderByAssigneeIdAsc(planId)) {
            results.add(RoleCheckSummaryResponse.from(recipient));
        }
        for (Confirmer confirmer : confirmerRepository.findByPlan_PlanIdOrderByConfirmIdAsc(planId)) {
            results.add(RoleCheckSummaryResponse.from(confirmer));
        }
        return results;
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
