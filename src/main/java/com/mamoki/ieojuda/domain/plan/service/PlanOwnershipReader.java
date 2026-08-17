package com.mamoki.ieojuda.domain.plan.service;

import java.util.UUID;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 로그인한 사용자가 자신의 계획만 조회할 수 있도록 검증 (불일치 시 존재 노출 방지를 위해 NOT_FOUND로 응답)
@Component
@RequiredArgsConstructor
public class PlanOwnershipReader {

    private final PlanRepository planRepository;

    public Plan findOwnedPlan(UUID userId, UUID planId) {
        return planRepository.findByPlanIdAndUser_UserId(planId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAN_NOT_FOUND));
    }
}
