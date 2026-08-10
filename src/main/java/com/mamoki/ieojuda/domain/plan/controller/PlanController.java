package com.mamoki.ieojuda.domain.plan.controller;

import com.mamoki.ieojuda.domain.plan.dto.CompilationResponse;
import com.mamoki.ieojuda.domain.plan.dto.PlanCreateRequest;
import com.mamoki.ieojuda.domain.plan.dto.PlanResponse;
import com.mamoki.ieojuda.domain.plan.dto.PlanUpdateRequest;
import com.mamoki.ieojuda.domain.plan.service.LifeAreaService;
import com.mamoki.ieojuda.domain.plan.service.PlanService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "새 계획 만들기" / "계획 홈" / "마이페이지" 화면 - 계획 생성·조회·수정·비활성화
@Tag(name = "Plan", description = "유고 계획 생성 / 조회 / 수정 / 비활성화")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService planService;
    private final LifeAreaService lifeAreaService;

    @Operation(summary = "계획 생성", description = "계획 이름과 대기 기간(7~30일)을 입력받아 계획을 생성합니다. 동시에 기본 삶의 구역(가족/관계 정리/업무 연속성) 3개가 자동 생성됩니다. 작성자는 Authorization 헤더의 Access Token으로 식별합니다.")
    @PostMapping
    public ResponseEntity<RsData<PlanResponse>> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PlanCreateRequest request
    ) {
        return ResponseEntity.ok(RsData.success(planService.create(userId, request)));
    }

    @Operation(summary = "계획 조회")
    @GetMapping("/{planId}")
    public ResponseEntity<RsData<PlanResponse>> getPlan(
            @Parameter(description = "계획 ID") @PathVariable Long planId
    ) {
        return ResponseEntity.ok(RsData.success(planService.getPlan(planId)));
    }

    @Operation(summary = "계획 수정", description = "계획 이름과 대기 기간을 수정합니다.")
    @PutMapping("/{planId}")
    public ResponseEntity<RsData<PlanResponse>> update(
            @Parameter(description = "계획 ID") @PathVariable Long planId,
            @Valid @RequestBody PlanUpdateRequest request
    ) {
        return ResponseEntity.ok(RsData.success(planService.update(planId, request)));
    }

    // 명세서 "AI 구조화 결과 검토" 화면 - 구역별 AI 구조화 결과 재조회 (id는 lifeAreaId)
    @Operation(summary = "AI 구조화 결과 조회", description = "삶의 구역별로 가장 최근 AI 구조화 원문 결과를 다시 조회합니다. 대화가 계속되어 재구조화될 때마다 최신 결과로 덮어써지므로 이전 이력은 조회할 수 없습니다.")
    @GetMapping("/{planId}/compilations/{lifeAreaId}")
    public ResponseEntity<RsData<CompilationResponse>> getCompilation(
            @Parameter(description = "계획 ID") @PathVariable Long planId,
            @Parameter(description = "삶의 구역 ID") @PathVariable Long lifeAreaId
    ) {
        return ResponseEntity.ok(RsData.success(lifeAreaService.getCompilation(planId, lifeAreaId)));
    }

    // 명세서 "마이페이지" API 호출: 계획 비활성화 POST /api/plans/{planId}/deactivate
    @Operation(summary = "계획 비활성화", description = "마이페이지에서 계획을 비활성화 상태로 전환합니다.")
    @PostMapping("/{planId}/deactivate")
    public ResponseEntity<RsData<PlanResponse>> deactivate(
            @Parameter(description = "계획 ID") @PathVariable Long planId
    ) {
        return ResponseEntity.ok(RsData.success(planService.deactivate(planId)));
    }
}
