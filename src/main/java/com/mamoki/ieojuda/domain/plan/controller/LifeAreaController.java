package com.mamoki.ieojuda.domain.plan.controller;

import com.mamoki.ieojuda.domain.plan.dto.LifeAreaResponse;
import com.mamoki.ieojuda.domain.plan.service.LifeAreaService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 명세서 "계획 홈" 화면 - 삶의 구역(가족/관계 정리/업무 연속성) 목록·상세 조회
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plans/{planId}/life-areas")
public class LifeAreaController {

    private final LifeAreaService lifeAreaService;

    @GetMapping
    public ResponseEntity<RsData<List<LifeAreaResponse>>> getLifeAreas(@PathVariable Long planId) {
        return ResponseEntity.ok(RsData.success(lifeAreaService.getLifeAreas(planId)));
    }

    @GetMapping("/{lifeAreaId}")
    public ResponseEntity<RsData<LifeAreaResponse>> getLifeArea(
            @PathVariable Long planId,
            @PathVariable Long lifeAreaId
    ) {
        return ResponseEntity.ok(RsData.success(lifeAreaService.getLifeArea(planId, lifeAreaId)));
    }
}
