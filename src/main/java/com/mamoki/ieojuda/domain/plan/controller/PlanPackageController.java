package com.mamoki.ieojuda.domain.plan.controller;

import com.mamoki.ieojuda.domain.plan.dto.PackagePreviewResponse;
import com.mamoki.ieojuda.domain.plan.dto.PlanResponse;
import com.mamoki.ieojuda.domain.plan.service.PlanPackageService;
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

// 명세서 "역할별 패키지 미리보기" - 작성자가 생전에 역할별 공개 내용을 검토하고 직접 승인(봉인)한다.
@Tag(name = "PlanPackage", description = "계획 - 역할별 패키지 미리보기 / 작성자 봉인")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plans/{planId}/packages")
public class PlanPackageController {

    private final PlanPackageService planPackageService;

    @Operation(summary = "패키지 미리보기", description = "역할별로 공개될 대상·행동·선행 조건을 담당자 단위로 묶어 보여줍니다. 다른 역할의 항목은 섞이지 않습니다.")
    @GetMapping("/preview")
    public ResponseEntity<RsData<PackagePreviewResponse>> getPreview(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "계획 ID") @PathVariable Long planId
    ) {
        return ResponseEntity.ok(RsData.success(planPackageService.getPreview(userId, planId)));
    }

    @Operation(summary = "패키지 봉인", description = "작성자가 역할별 패키지를 최종 승인합니다. 수락 확인자 2명 미만, 금지정보 포함, 원문 근거 없는 항목이 있으면 차단됩니다.")
    @PostMapping("/seal")
    public ResponseEntity<RsData<PlanResponse>> seal(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "계획 ID") @PathVariable Long planId
    ) {
        return ResponseEntity.ok(RsData.success(planPackageService.seal(userId, planId)));
    }
}
