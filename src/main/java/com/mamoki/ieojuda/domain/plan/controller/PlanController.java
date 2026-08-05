package com.mamoki.ieojuda.domain.plan.controller;

import com.mamoki.ieojuda.domain.plan.dto.PlanCreateRequest;
import com.mamoki.ieojuda.domain.plan.dto.PlanResponse;
import com.mamoki.ieojuda.domain.plan.dto.PlanUpdateRequest;
import com.mamoki.ieojuda.domain.plan.service.PlanService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "새 계획 만들기" / "계획 홈" / "마이페이지" 화면 - 계획 생성·조회·수정·비활성화
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService planService;

    @PostMapping
    public ResponseEntity<RsData<PlanResponse>> create(@Valid @RequestBody PlanCreateRequest request) {
        return ResponseEntity.ok(RsData.success(planService.create(request)));
    }

    @GetMapping("/{planId}")
    public ResponseEntity<RsData<PlanResponse>> getPlan(@PathVariable Long planId) {
        return ResponseEntity.ok(RsData.success(planService.getPlan(planId)));
    }

    @PutMapping("/{planId}")
    public ResponseEntity<RsData<PlanResponse>> update(
            @PathVariable Long planId,
            @Valid @RequestBody PlanUpdateRequest request
    ) {
        return ResponseEntity.ok(RsData.success(planService.update(planId, request)));
    }

    // 명세서 "마이페이지" API 호출: 계획 비활성화 POST /api/plans/{planId}/deactivate
    @PostMapping("/{planId}/deactivate")
    public ResponseEntity<RsData<PlanResponse>> deactivate(@PathVariable Long planId) {
        return ResponseEntity.ok(RsData.success(planService.deactivate(planId)));
    }
}
