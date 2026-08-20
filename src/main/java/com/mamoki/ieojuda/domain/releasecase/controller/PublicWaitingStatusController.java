package com.mamoki.ieojuda.domain.releasecase.controller;

import java.util.UUID;

import com.mamoki.ieojuda.domain.releasecase.dto.PublicWaitingStatusResponse;
import com.mamoki.ieojuda.domain.releasecase.service.PublicWaitingStatusService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "사후 인계" 화면 - 경고 메일의 waiting 링크로 접근 (로그인 불필요, CANCEL_CASE/RAISE_OBJECTION
// 토큰이 곧 인증). ReleaseStatusController.getStatus()(로그인 필요)와 같은 화면을 보여주되 인증 수단만 다르다.
@Tag(name = "PublicWaitingStatus", description = "대기 상태 공개 조회 (취소/이의제기 링크)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/release-cases/{caseId}/waiting")
public class PublicWaitingStatusController {

    private final PublicWaitingStatusService publicWaitingStatusService;

    @Operation(summary = "대기 상태 공개 조회", description = "경고 메일 링크의 토큰으로 로그인 없이 상태를 조회하고, 이 토큰으로 취소/이의제기 중 무엇이 가능한지 함께 반환합니다.")
    @GetMapping("/status")
    public ResponseEntity<RsData<PublicWaitingStatusResponse>> getStatus(
            @Parameter(description = "사건 ID") @PathVariable UUID caseId,
            @Parameter(description = "경고 메일 링크 토큰") @RequestParam String token
    ) {
        return ResponseEntity.ok(RsData.success(publicWaitingStatusService.getStatus(caseId, token)));
    }
}
