package com.mamoki.ieojuda.domain.handoffcheck.controller;

import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckSendRequest;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckSendResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// "선택형 생전 인계 점검" 화면 - 역할 담당자/지정 확인자 준비 상태 조회 및 점검 메일 발송
@Tag(name = "HandoffCheck", description = "선택형 생전 인계 점검 - 담당자/확인자 준비 상태 조회 및 점검 메일 발송")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plans/{planId}/handoff-checks")
public class HandoffCheckController {

    private final HandoffCheckService handoffCheckService;

    @Operation(summary = "인계 점검 화면 조회", description = "역할 담당자와 지정 확인자의 이메일 발송, 역할 수락, 대체 담당자, 문의 사항, 준비 완료 여부를 함께 조회합니다.")
    @GetMapping
    public ResponseEntity<RsData<HandoffCheckStatusResponse>> getHandoffCheck(
            @AuthenticationPrincipal Long userId, // 현재 로그인한 사용자 ID 식별
            @Parameter(description = "계획 ID") @PathVariable Long planId
    ) {
        HandoffCheckStatusResponse result = handoffCheckService.getHandoffCheck(userId, planId);
        return ResponseEntity.ok(RsData.success(result));
    }

    // recipientIds는 선택 입력이라 바디 자체를 생략해도 받아야 하므로 required = false
    @Operation(summary = "인계 점검 발송", description = "봉인된 계획의 담당자에게 만료형 링크의 점검 메일을 발송합니다. recipientIds를 생략하면 대체 담당자를 제외한 전체 담당자에게 발송합니다.")
    @PostMapping
    public ResponseEntity<RsData<HandoffCheckSendResponse>> sendCheck(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "계획 ID") @PathVariable Long planId,
            @RequestBody(required = false) HandoffCheckSendRequest request
    ) {
        HandoffCheckSendResponse result = handoffCheckService.sendCheck(userId, planId, request);
        return ResponseEntity.ok(RsData.success(result));
    }
}
