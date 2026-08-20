package com.mamoki.ieojuda.domain.stage.controller;

import java.util.UUID;

import com.mamoki.ieojuda.domain.stage.dto.HandoverStageResponse;
import com.mamoki.ieojuda.domain.stage.service.HandoverStageService;
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

// 명세서 "단계 완료 / 대체 담당자" 화면 - 역할 담당자·서비스 운영자 공용
// issue #59 - CASE_SUPERVISE 세부 권한 검사
@Tag(name = "Handover Stage", description = "단계 완료 확인 / 대체 담당자 전환")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/release-cases/{caseId}/stages/{stageId}")
public class HandoverStageController {

    private final HandoverStageService handoverStageService;

    @Operation(summary = "단계 상태 조회", description = "현재 단계, 완료 조건, 남은 시간, 담당자 정보를 조회합니다.")
    @GetMapping
    public ResponseEntity<RsData<HandoverStageResponse>> getStage(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "사건 ID") @PathVariable UUID caseId,
            @Parameter(description = "단계 ID") @PathVariable UUID stageId
    ) {
        return ResponseEntity.ok(RsData.success(handoverStageService.getStage(userId, caseId, stageId)));
    }

    @Operation(summary = "대체 담당자 전환", description = "무응답·영구 반송·문제 신고 시 사전 등록된 대체 담당자로 전환하고 안내 메일을 발송합니다. 대체 담당자가 없으면 사건을 차단 상태로 유지합니다.")
    @PostMapping("/fallback")
    public ResponseEntity<RsData<HandoverStageResponse>> fallback(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "사건 ID") @PathVariable UUID caseId,
            @Parameter(description = "단계 ID") @PathVariable UUID stageId
    ) {
        return ResponseEntity.ok(RsData.success(handoverStageService.fallback(userId, caseId, stageId)));
    }
}
