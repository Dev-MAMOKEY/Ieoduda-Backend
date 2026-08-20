package com.mamoki.ieojuda.domain.releasecase.controller;

import java.util.UUID;

import com.mamoki.ieojuda.domain.releasecase.dto.ReleaseCaseDetailResponse;
import com.mamoki.ieojuda.domain.releasecase.service.ReleaseCaseDetailService;
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

// 관리자 화면 - caseId로 임의의 사건을 상세 조회 (소유자 본인 여부와 무관)
@Tag(name = "Admin - ReleaseCase", description = "운영관리자 - 사건 상세 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/release-cases/{caseId}")
public class ReleaseCaseController {

    private final ReleaseCaseDetailService releaseCaseDetailService;

    @Operation(summary = "관리자 사건 상세 조회", description = "caseId로 사건의 상태·이력·소유자 정보를 조회합니다.")
    @GetMapping
    public ResponseEntity<RsData<ReleaseCaseDetailResponse>> getDetail(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "사건 ID") @PathVariable UUID caseId
    ) {
        return ResponseEntity.ok(RsData.success(releaseCaseDetailService.getDetail(userId, caseId)));
    }
}
