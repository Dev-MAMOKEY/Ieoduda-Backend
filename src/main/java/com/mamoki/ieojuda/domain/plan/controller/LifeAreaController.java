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

// 명세서 "계획 홈" 화면 - 삶의 구역(가족/관계 정리/업무 연속성) 목록·상세 조회
@Tag(name = "LifeArea", description = "삶의 구역(가족/관계 정리/업무 연속성) 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plans/{planId}/life-areas")
public class LifeAreaController {

    private final LifeAreaService lifeAreaService;

    @Operation(summary = "삶의 구역 목록 조회", description = "계획에 속한 삶의 구역 3개(가족/관계 정리/업무 연속성)를 조회합니다.")
    @GetMapping
    public ResponseEntity<RsData<List<LifeAreaResponse>>> getLifeAreas(
            @Parameter(description = "계획 ID") @PathVariable Long planId
    ) {
        return ResponseEntity.ok(RsData.success(lifeAreaService.getLifeAreas(planId)));
    }

    @Operation(summary = "삶의 구역 단건 조회")
    @GetMapping("/{lifeAreaId}")
    public ResponseEntity<RsData<LifeAreaResponse>> getLifeArea(
            @Parameter(description = "계획 ID") @PathVariable Long planId,
            @Parameter(description = "삶의 구역 ID") @PathVariable Long lifeAreaId
    ) {
        return ResponseEntity.ok(RsData.success(lifeAreaService.getLifeArea(planId, lifeAreaId)));
    }
}
