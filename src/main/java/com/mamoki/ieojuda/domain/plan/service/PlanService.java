package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.plan.dto.PlanResponse;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 계획(Plan)은 더 이상 사용자가 "만드는" 대상이 아니다 - 회원가입 시 자동으로 1개 생성되고(AuthService),
// 사망확인/증빙검토/발송 파이프라인(death_confirmers, release_cases 등)이 매달리는 앵커 역할만 한다.
// 그래서 이 서비스에는 생성/수정 로직이 없고 조회·비활성화만 남는다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanService {

    private final PlanRepository planRepository;

    public PlanResponse getPlan(Long planId) {
        return PlanResponse.from(findPlan(planId));
    }

    // 로그인한 사용자가 자기 planId를 모를 때(로그인 직후 등) 조회
    public PlanResponse getMyPlan(Long userId) {
        Plan plan = planRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAN_NOT_FOUND));
        return PlanResponse.from(plan);
    }

    @Transactional
    public PlanResponse deactivate(Long planId) {
        Plan plan = findPlan(planId);
        plan.deactivate();
        return PlanResponse.from(plan);
    }

    private Plan findPlan(Long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAN_NOT_FOUND));
    }
}
