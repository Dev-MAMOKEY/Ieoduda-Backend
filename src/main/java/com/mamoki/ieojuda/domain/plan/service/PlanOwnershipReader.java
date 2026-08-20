package com.mamoki.ieojuda.domain.plan.service;

import java.util.UUID;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanStatus;
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

    // 마이페이지에서 계획을 비활성화한 뒤에는 담당자·항목 등 하위 자원을 더 이상 정상적으로 다룰 수
    // 없어야 한다. findOwnedPlan()은 계획 상태 조회(예: 계획 홈, 마이페이지) 자체는 계속 되어야 하므로
    // 그대로 두고, 비활성화 여부를 신경 써야 하는 서비스만 이 메서드를 쓰도록 분리한다.
    public Plan findOwnedActivePlan(UUID userId, UUID planId) {
        Plan plan = findOwnedPlan(userId, planId);
        if (plan.getStatus() == PlanStatus.DEACTIVATED) {
            throw new CustomException(ErrorCode.PLAN_DEACTIVATED);
        }
        return plan;
    }
}
