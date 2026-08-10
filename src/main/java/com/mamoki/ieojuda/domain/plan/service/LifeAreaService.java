package com.mamoki.ieojuda.domain.plan.service;

import tools.jackson.databind.ObjectMapper;
import com.mamoki.ieojuda.domain.plan.dto.AiTurnResult;
import com.mamoki.ieojuda.domain.plan.dto.CompilationResponse;
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
    private final ObjectMapper objectMapper;

    public List<LifeAreaResponse> getLifeAreas(Long planId) {
        return lifeAreaRepository.findByPlan_PlanIdOrderByLifeIdAsc(planId).stream()
                .map(LifeAreaResponse::from)
                .toList();
    }

    public LifeAreaResponse getLifeArea(Long planId, Long lifeAreaId) {
        return LifeAreaResponse.from(findLifeArea(planId, lifeAreaId));
    }

    // 명세서 "AI 구조화 결과 검토" 화면 - 새 엔티티 없이 LifeArea.aiStructuredResult(구역별 최신 AI 구조화 원문)를 재조회
    public CompilationResponse getCompilation(Long planId, Long lifeAreaId) {
        LifeArea lifeArea = findLifeArea(planId, lifeAreaId);
        String raw = lifeArea.getAiStructuredResult();

        if (raw == null || raw.isBlank()) {
            return new CompilationResponse(lifeArea.getLifeId(), lifeArea.getCategory().name(), true, List.of());
        }

        AiTurnResult result = parseAiTurnResult(raw);
        return new CompilationResponse(lifeArea.getLifeId(), lifeArea.getCategory().name(), false, result.items());
    }

    private AiTurnResult parseAiTurnResult(String rawContent) {
        try {
            return objectMapper.readValue(rawContent, AiTurnResult.class);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private LifeArea findLifeArea(Long planId, Long lifeAreaId) {
        LifeArea lifeArea = lifeAreaRepository.findById(lifeAreaId)
                .orElseThrow(() -> new CustomException(ErrorCode.LIFE_AREA_NOT_FOUND));
        if (!lifeArea.getPlan().getPlanId().equals(planId)) {
            throw new CustomException(ErrorCode.LIFE_AREA_NOT_FOUND);
        }
        return lifeArea;
    }
}
