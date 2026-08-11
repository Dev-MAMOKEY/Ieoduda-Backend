package com.mamoki.ieojuda.domain.plan.controller;

import com.mamoki.ieojuda.domain.plan.dto.LifeAreaResponse;
import com.mamoki.ieojuda.domain.plan.service.LifeAreaService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 명세서 "계획 홈" / "AI 구조화 결과 검토" 화면 - 카테고리(가족/관계 정리/업무 연속성)별 항목 조회
// 특정 lifeAreaId를 콕 집어 조회하는 API는 없앴다 - 같은 카테고리라도 대화 세션마다 LifeArea 행이 여러 개 생길 수 있어서,
// 항상 plan 전체를 카테고리 기준으로 집계해서 보여준다.
@Tag(name = "LifeArea", description = "삶의 구역(가족/관계 정리/업무 연속성)별 항목 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plans/{planId}/life-areas")
public class LifeAreaController {

    private final LifeAreaService lifeAreaService;

    @Operation(summary = "삶의 구역별 항목 조회", description = "가족/관계 정리/업무 연속성 3개 구역 각각에 지금까지 쌓인 항목 전체를 조회합니다.")
    @GetMapping
    public ResponseEntity<RsData<List<LifeAreaResponse>>> getLifeAreas(
            @Parameter(description = "계획 ID") @PathVariable Long planId
    ) {
        return ResponseEntity.ok(RsData.success(lifeAreaService.getLifeAreas(planId)));
    }
}
