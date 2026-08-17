package com.mamoki.ieojuda.domain.handoffcheck.controller;

import java.util.UUID;

import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckStatusResponse;
import com.mamoki.ieojuda.domain.handoffcheck.service.HandoffCheckService;
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

// "선택형 생전 인계 점검" 화면 - 역할 담당자/지정 확인자 준비 상태 조회
@Tag(name = "HandoffCheck", description = "선택형 생전 인계 점검 - 담당자/확인자 준비 상태 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plans/{planId}/handoff-checks")
public class HandoffCheckController {

    private final HandoffCheckService handoffCheckService;

    @Operation(summary = "인계 점검 화면 조회", description = "역할 담당자와 지정 확인자의 이메일 발송, 역할 수락, 대체 담당자, 문의 사항, 준비 완료 여부를 함께 조회합니다.")
    @GetMapping
    public ResponseEntity<RsData<HandoffCheckStatusResponse>> getHandoffCheck(
            @AuthenticationPrincipal UUID userId, // 현재 로그인한 사용자 ID 식별
            @Parameter(description = "계획 ID") @PathVariable UUID planId
    ) {
        HandoffCheckStatusResponse result = handoffCheckService.getHandoffCheck(userId, planId);
        return ResponseEntity.ok(RsData.success(result));
    }
}
