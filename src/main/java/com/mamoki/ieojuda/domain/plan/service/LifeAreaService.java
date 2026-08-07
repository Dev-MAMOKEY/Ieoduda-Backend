package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.plan.dto.LifeAreaResponse;
import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 명세서 "계획 홈" 화면 - 계획에 속한 삶의 구역(가족/관계 정리/업무 연속성) 조회
// 구역은 계획 생성 시 3개가 자동으로 만들어지므로 별도의 생성/삭제 API는 두지 않는다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LifeAreaService {

    private final LifeAreaRepository lifeAreaRepository;

    public List<LifeAreaResponse> getLifeAreas(Long planId) {
        return lifeAreaRepository.findByPlan_PlanIdOrderByLifeIdAsc(planId).stream()
                .map(LifeAreaResponse::from)
                .toList();
    }

    public LifeAreaResponse getLifeArea(Long planId, Long lifeAreaId) {
        LifeArea lifeArea = lifeAreaRepository.findById(lifeAreaId)
                .orElseThrow(() -> new CustomException(ErrorCode.LIFE_AREA_NOT_FOUND));
        if (!lifeArea.getPlan().getPlanId().equals(planId)) {
            throw new CustomException(ErrorCode.LIFE_AREA_NOT_FOUND);
        }
        return LifeAreaResponse.from(lifeArea);
    }
}
