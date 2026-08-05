package com.mamoki.ieojuda.domain.plan.controller;

import com.mamoki.ieojuda.domain.plan.dto.PlanOptionsRequest;
import com.mamoki.ieojuda.domain.plan.service.PlanOptionsService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "새 계획 만들기" 화면 - "세부사항 대화하기" 버튼: 가족/관계정리/업무정리 선택값을 한 번에 저장
@Tag(name = "Plan Options", description = "새 계획 만들기 - 구역별 초기 선택값(세부사항 대화하기) 저장")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plans/{planId}/options")
public class PlanOptionsController {

    private final PlanOptionsService planOptionsService;

    @Operation(summary = "구역별 초기 선택값 저장", description = "가족/관계정리/업무정리 3개 구역의 선택값을 한 번에 받아 각 삶의 구역에 나눠 저장합니다. 이후 삶의 구역 생성(대화) 화면에서 AI가 이 값을 기준으로 대화를 이어갑니다.")
    @PostMapping
    public ResponseEntity<RsData<Void>> saveOptions(
            @Parameter(description = "계획 ID") @PathVariable Long planId,
            @RequestBody PlanOptionsRequest request
    ) {
        planOptionsService.saveOptions(planId, request);
        return ResponseEntity.ok(RsData.success(null));
    }
}
