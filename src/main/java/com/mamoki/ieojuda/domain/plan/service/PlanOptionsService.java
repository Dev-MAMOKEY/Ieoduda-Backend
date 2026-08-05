package com.mamoki.ieojuda.domain.plan.service;

import tools.jackson.databind.ObjectMapper;
import com.mamoki.ieojuda.domain.plan.dto.ObituaryDelivery;
import com.mamoki.ieojuda.domain.plan.dto.OngoingWorkHandover;
import com.mamoki.ieojuda.domain.plan.dto.PlanOptionsRequest;
import com.mamoki.ieojuda.domain.plan.dto.SnsAction;
import com.mamoki.ieojuda.domain.plan.dto.WorkAccountAction;
import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 명세서 "새 계획 만들기" 화면 - "세부사항 대화하기" 버튼으로 넘어온 구역별 초기 선택값을
// 각 삶의 구역(LifeArea)의 rawText에 나눠 저장한다. (rawText는 원래 "이 구역의 최초 입력"을
// 담는 자리라 새 컬럼을 만들지 않고 그대로 재사용)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanOptionsService {

    private final PlanRepository planRepository;
    private final LifeAreaRepository lifeAreaRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveOptions(Long planId, PlanOptionsRequest request) {
        if (!planRepository.existsById(planId)) {
            throw new CustomException(ErrorCode.PLAN_NOT_FOUND);
        }

        List<LifeArea> lifeAreas = lifeAreaRepository.findByPlan_PlanIdOrderByLifeIdAsc(planId);
        for (LifeArea lifeArea : lifeAreas) {
            String seedText = switch (lifeArea.getCategory()) {
                case FAMILY -> toJson(new FamilyOptions(request.familyMessage()));
                case RELATIONSHIP_CLEANUP -> toJson(new RelationshipOptions(
                        request.snsAction(), request.snsOtherDetail(),
                        request.obituaryDelivery(), request.closeFriendName()));
                case WORK_CONTINUITY -> toJson(new WorkOptions(
                        request.workAccountAction(), request.ongoingWorkHandover(), request.handoverDetail()));
            };
            lifeArea.applyRawText(seedText);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private record FamilyOptions(String familyMessage) {
    }

    private record RelationshipOptions(SnsAction snsAction, String snsOtherDetail,
                                        ObituaryDelivery obituaryDelivery, String closeFriendName) {
    }

    private record WorkOptions(WorkAccountAction workAccountAction,
                                OngoingWorkHandover ongoingWorkHandover, String handoverDetail) {
    }
}
