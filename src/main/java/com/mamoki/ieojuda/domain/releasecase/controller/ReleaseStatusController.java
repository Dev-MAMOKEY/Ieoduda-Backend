package com.mamoki.ieojuda.domain.releasecase.controller;

import com.mamoki.ieojuda.domain.releasecase.dto.ReleaseStatusResponse;
import com.mamoki.ieojuda.domain.releasecase.service.ReleaseStatusService;
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

// 명세서 "사후 인계" 화면 - 작성자 본인용 대기 상태 조회 / 취소
@Tag(name = "ReleaseStatus", description = "사후 인계 - 작성자 본인 대기 상태 조회 / 취소")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plans/{planId}/release-status")
public class ReleaseStatusController {

    private final ReleaseStatusService releaseStatusService;

    @Operation(summary = "대기 상태 조회", description = "진행 중인 사후 인계 사건의 상태, 예정 발송일, 남은 기간을 조회합니다. 진행 중인 사건이 없으면 hasActiveCase가 false로 옵니다.")
    @GetMapping
    public ResponseEntity<RsData<ReleaseStatusResponse>> getStatus(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "계획 ID") @PathVariable Long planId
    ) {
        return ResponseEntity.ok(RsData.success(releaseStatusService.getStatus(userId, planId)));
    }

    @Operation(summary = "본인 확인 후 취소하기", description = "진행 중인 절차 전체를 즉시 취소합니다.")
    @PostMapping("/cancel")
    public ResponseEntity<RsData<ReleaseStatusResponse>> cancel(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "계획 ID") @PathVariable Long planId
    ) {
        return ResponseEntity.ok(RsData.success(releaseStatusService.cancel(userId, planId)));
    }
}
