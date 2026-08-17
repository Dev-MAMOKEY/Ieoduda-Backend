package com.mamoki.ieojuda.domain.rolecheck.controller;

import java.util.UUID;

import com.mamoki.ieojuda.domain.rolecheck.dto.RoleCheckSummaryResponse;
import com.mamoki.ieojuda.domain.rolecheck.service.RoleCheckService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// "점검" 화면 - "역할 점검" 탭 상단 이름 목록 (역할 담당자 + 지정 확인자)
@Tag(name = "RoleCheck", description = "역할 점검 - 담당자/확인자 통합 목록 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plans/{planId}/role-checks")
public class RoleCheckController {

    private final RoleCheckService roleCheckService;

    @Operation(summary = "역할 점검 대상 목록 조회", description = "역할 점검 화면 상단에 표시할 역할 담당자와 지정 확인자 이름 목록을 함께 조회합니다. 대체 담당자는 포함하지 않습니다.")
    @GetMapping
    public ResponseEntity<RsData<List<RoleCheckSummaryResponse>>> getRoleChecks(
            @AuthenticationPrincipal UUID userId, // 현재 로그인한 사용자 ID 식별
            @Parameter(description = "계획 ID") @PathVariable UUID planId
    ) {
        List<RoleCheckSummaryResponse> result = roleCheckService.getRoleChecks(userId, planId);
        return ResponseEntity.ok(RsData.success(result));
    }
}
