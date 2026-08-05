package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.plan.dto.PlanCreateRequest;
import com.mamoki.ieojuda.domain.plan.dto.PlanResponse;
import com.mamoki.ieojuda.domain.plan.dto.PlanUpdateRequest;
import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import com.mamoki.ieojuda.domain.plan.entity.LifeAreaCategory;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 명세서 "새 계획 만들기" / "계획 홈" / "마이페이지" 화면 - 계획 생성·조회·수정·비활성화
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanService {

    private static final int MIN_WAITING_DAYS = 7;
    private static final int MAX_WAITING_DAYS = 30;

    private final PlanRepository planRepository;
    private final LifeAreaRepository lifeAreaRepository;
    private final UserRepository userRepository;

    @Transactional
    public PlanResponse create(PlanCreateRequest request) {
        validateWaitingDays(request.waitingDays());

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Plan plan = planRepository.save(Plan.builder()
                .user(user)
                .name(request.name())
                .waitingDays(request.waitingDays())
                .build());

        // 명세서 "새 계획 만들기" 표시 요소: 기본 구역(가족/관계 정리/업무 연속성)을 함께 생성
        for (LifeAreaCategory category : LifeAreaCategory.values()) {
            lifeAreaRepository.save(LifeArea.builder()
                    .plan(plan)
                    .category(category)
                    .build());
        }

        return PlanResponse.from(plan);
    }

    public PlanResponse getPlan(Long planId) {
        return PlanResponse.from(findPlan(planId));
    }

    @Transactional
    public PlanResponse update(Long planId, PlanUpdateRequest request) {
        validateWaitingDays(request.waitingDays());
        Plan plan = findPlan(planId);
        plan.updateInfo(request.name(), request.waitingDays());
        return PlanResponse.from(plan);
    }

    @Transactional
    public PlanResponse deactivate(Long planId) {
        Plan plan = findPlan(planId);
        plan.deactivate();
        return PlanResponse.from(plan);
    }

    private void validateWaitingDays(Integer waitingDays) {
        // 명세서 예외 처리: 7일 미만 또는 30일 초과 값을 저장하지 않음
        if (waitingDays == null || waitingDays < MIN_WAITING_DAYS || waitingDays > MAX_WAITING_DAYS) {
            throw new CustomException(ErrorCode.INVALID_WAITING_PERIOD);
        }
    }

    private Plan findPlan(Long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAN_NOT_FOUND));
    }
}
