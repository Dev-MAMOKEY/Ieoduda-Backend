package com.mamoki.ieojuda.domain.releasecase.controller;

import java.util.UUID;

import com.mamoki.ieojuda.domain.releasecase.dto.CaseCancellationRequest;
import com.mamoki.ieojuda.domain.releasecase.dto.ReleaseStatusResponse;
import com.mamoki.ieojuda.domain.releasecase.service.CaseCancellationService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "사후 인계" 화면 - 경고 메일의 취소 링크로 접수 (로그인 불필요, 초대 토큰이 곧 인증)
@Tag(name = "CaseCancellation", description = "경고 메일 취소 링크 접수")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/release-cases/{caseId}/cancellations")
public class CaseCancellationController {

    private final CaseCancellationService caseCancellationService;

    @Operation(summary = "취소 링크로 취소하기", description = "사건 생성 시 발송된 경고 메일의 취소 링크로 진행 중인 절차 전체를 즉시 취소합니다.")
    @PostMapping
    public ResponseEntity<RsData<ReleaseStatusResponse>> cancel(
            @Parameter(description = "사건 ID") @PathVariable UUID caseId,
            @Valid @RequestBody CaseCancellationRequest request
    ) {
        return ResponseEntity.ok(RsData.success(caseCancellationService.cancel(caseId, request.token())));
    }
}
