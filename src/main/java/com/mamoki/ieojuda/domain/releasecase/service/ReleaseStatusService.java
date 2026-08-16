package com.mamoki.ieojuda.domain.releasecase.service;

import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.service.PlanOwnershipReader;
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

    private final PlanOwnershipReader planOwnershipReader;
    private final ReleaseCaseRepository releaseCaseRepository;

    public ReleaseStatusResponse getStatus(Long userId, Long planId) {
        Plan plan = planOwnershipReader.findOwnedPlan(userId, planId);
        ReleaseCase releaseCase = releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(plan.getPlanId()).orElse(null);
        return ReleaseStatusResponse.of(releaseCase);
    }

    // "살아계신가요? 지금 멈출 수 있어요" - 본인 확인 후 절차 전체를 즉시 취소
    @Transactional
    public ReleaseStatusResponse cancel(Long userId, Long planId) {
        Plan plan = planOwnershipReader.findOwnedPlan(userId, planId);
        ReleaseCase releaseCase = releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(plan.getPlanId())
                .orElseThrow(() -> new CustomException(ErrorCode.RELEASE_CASE_NOT_FOUND));

        releaseCase.cancel();
        return ReleaseStatusResponse.of(releaseCase);
    }
}
