package com.mamoki.ieojuda.domain.plan.controller;

import com.mamoki.ieojuda.domain.plan.dto.PlanResponse;
import com.mamoki.ieojuda.domain.plan.service.PlanService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "마이페이지" 화면 - 계획(사후 인계 케이스) 조회·비활성화
// 계획은 더 이상 사용자가 직접 만드는 대상이 아니라, 회원가입 시 자동으로 1개 생성된다(AuthService.signup 참고).
@Tag(name = "Plan", description = "유고 계획(사후 인계 케이스) 조회 / 비활성화")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService planService;

    @Operation(summary = "내 계획 조회", description = "로그인한 사용자의 계획을 조회합니다. 계획은 회원가입 시 자동으로 1개 생성되므로, planId를 아직 모를 때(로그인 직후 등) 사용합니다.")
    @GetMapping("/me")
    public ResponseEntity<RsData<PlanResponse>> getMyPlan(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(RsData.success(planService.getMyPlan(userId)));
    }

    @Operation(summary = "계획 조회")
    @GetMapping("/{planId}")
    public ResponseEntity<RsData<PlanResponse>> getPlan(
            @Parameter(description = "계획 ID") @PathVariable Long planId
    ) {
        return ResponseEntity.ok(RsData.success(planService.getPlan(planId)));
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
