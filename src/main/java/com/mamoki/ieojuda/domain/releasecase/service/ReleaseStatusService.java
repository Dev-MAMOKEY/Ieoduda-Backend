package com.mamoki.ieojuda.domain.releasecase.service;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.domain.releasecase.dto.ReleaseStatusResponse;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 명세서 "사후 인계" 화면 - 작성자 본인이 자기 계획의 대기 상태를 조회하고, 필요하면 직접 취소한다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReleaseStatusService {

    private final PlanRepository planRepository;
    private final ReleaseCaseRepository releaseCaseRepository;

    public ReleaseStatusResponse getStatus(Long userId, Long planId) {
        Plan plan = findOwnedPlan(userId, planId);
        ReleaseCase releaseCase = releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(plan.getPlanId()).orElse(null);
        return ReleaseStatusResponse.of(releaseCase);
    }

    // "살아계신가요? 지금 멈출 수 있어요" - 본인 확인 후 절차 전체를 즉시 취소
    @Transactional
    public ReleaseStatusResponse cancel(Long userId, Long planId) {
        Plan plan = findOwnedPlan(userId, planId);
        ReleaseCase releaseCase = releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(plan.getPlanId())
                .orElseThrow(() -> new CustomException(ErrorCode.RELEASE_CASE_NOT_FOUND));

        releaseCase.cancel();
        return ReleaseStatusResponse.of(releaseCase);
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
