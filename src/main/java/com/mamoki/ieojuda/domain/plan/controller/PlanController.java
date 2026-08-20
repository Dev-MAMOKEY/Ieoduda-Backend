package com.mamoki.ieojuda.domain.plan.controller;

import java.util.UUID;

import com.mamoki.ieojuda.domain.plan.dto.PlanResponse;
import com.mamoki.ieojuda.domain.plan.dto.PlanSummaryResponse;
import com.mamoki.ieojuda.domain.plan.dto.ReleasePolicyRequest;
import com.mamoki.ieojuda.domain.plan.dto.ReleasePolicyResponse;
import com.mamoki.ieojuda.domain.plan.dto.ReleaseSettingsResponse;
import com.mamoki.ieojuda.domain.plan.dto.SelfWarningEmailRequest;
import com.mamoki.ieojuda.domain.plan.dto.SelfWarningEmailResponse;
import com.mamoki.ieojuda.domain.plan.service.PlanService;
import com.mamoki.ieojuda.domain.plan.service.PlanSummaryService;
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

// 명세서 "마이페이지" 화면 - 계획(사후 인계 케이스) 조회·비활성화
// 계획은 더 이상 사용자가 직접 만드는 대상이 아니라, 회원가입 시 자동으로 1개 생성된다(AuthService.signup 참고).
@Tag(name = "Plan", description = "유고 계획(사후 인계 케이스) 조회 / 비활성화")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService planService;
    private final PlanSummaryService planSummaryService;

    // issue #94 - 명세서 "계획 홈" 경로(GET /api/plans/current/summary)와 실제 구현(GET /api/plans/me)이
    // 어긋나 있던 것을 명세서 경로로 정렬했다. 명세서의 "GET /api/release-cases/current"(사후 사건 상태)는
    // 별도 엔드포인트가 아니라 이 응답의 releaseCase 필드로 흡수되어 있다(issue #84).
    @Operation(summary = "내 계획 조회", description = "로그인한 사용자의 계획 홈 요약을 조회합니다. 계획은 회원가입 시 자동으로 1개 생성되므로, planId를 아직 모를 때(로그인 직후 등) 사용합니다.")
    @GetMapping("/current/summary")
    public ResponseEntity<RsData<PlanSummaryResponse>> getMyPlan(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(RsData.success(planSummaryService.getMySummary(userId)));
    }

    @Operation(summary = "계획 조회")
    @GetMapping("/{planId}")
    public ResponseEntity<RsData<PlanResponse>> getPlan(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "계획 ID") @PathVariable UUID planId
    ) {
        return ResponseEntity.ok(RsData.success(planService.getPlan(userId, planId)));
    }

    @Operation(summary = "대기·이의제기 설정 조회", description = "저장된 대기 기간, 본인 경고 이메일, 이의 제기 연락처를 한 번에 조회합니다. 아직 등록 안 한 항목은 null로 옵니다.")
    @GetMapping("/{planId}/release-policy")
    public ResponseEntity<RsData<ReleaseSettingsResponse>> getReleaseSettings(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "계획 ID") @PathVariable UUID planId
    ) {
        return ResponseEntity.ok(RsData.success(planService.getReleaseSettings(userId, planId)));
    }

    @Operation(summary = "대기 기간 설정", description = "증빙 승인 후 실제 발송까지 기다리는 대기 기간(7~30일)을 저장합니다.")
    @PutMapping("/{planId}/release-policy")
    public ResponseEntity<RsData<ReleasePolicyResponse>> updateReleasePolicy(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "계획 ID") @PathVariable UUID planId,
            @Valid @RequestBody ReleasePolicyRequest request
    ) {
        return ResponseEntity.ok(RsData.success(planService.updateReleasePolicy(userId, planId, request)));
    }

    @Operation(summary = "본인 경고 이메일 등록", description = "실행 신고가 오면 가장 먼저 경고 메일을 받을 본인 주소를 등록하고, 그 주소로 검증 메일을 발송합니다.")
    @PostMapping("/{planId}/self-warning-email")
    public ResponseEntity<RsData<SelfWarningEmailResponse>> requestSelfWarningEmailVerification(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "계획 ID") @PathVariable UUID planId,
            @Valid @RequestBody SelfWarningEmailRequest request
    ) {
        return ResponseEntity.ok(RsData.success(planService.requestSelfWarningEmailVerification(userId, planId, request)));
    }

    // 명세서 "마이페이지" API 호출: 계획 비활성화 POST /api/plans/{planId}/deactivate
    @Operation(summary = "계획 비활성화", description = "마이페이지에서 계획을 비활성화 상태로 전환합니다.")
    @PostMapping("/{planId}/deactivate")
    public ResponseEntity<RsData<PlanResponse>> deactivate(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "계획 ID") @PathVariable UUID planId
    ) {
        return ResponseEntity.ok(RsData.success(planService.deactivate(userId, planId)));
    }
}
