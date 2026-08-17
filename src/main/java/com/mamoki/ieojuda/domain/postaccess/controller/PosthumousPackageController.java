package com.mamoki.ieojuda.domain.postaccess.controller;

import com.mamoki.ieojuda.domain.postaccess.dto.PackageActionResponse;
import com.mamoki.ieojuda.domain.postaccess.dto.PackageIssueRequest;
import com.mamoki.ieojuda.domain.postaccess.dto.PackageIssueResponse;
import com.mamoki.ieojuda.domain.postaccess.dto.PosthumousPackageResponse;
import com.mamoki.ieojuda.domain.postaccess.service.PosthumousPackageService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "역할별 사후 패키지" - OTP 인증(#76)을 통과한 담당자가 자신의 역할 항목만 확인·완료·신고
// (로그인 불필요, 열람 세션(accessSessionId)이 곧 인증)
@Tag(name = "PosthumousPackage", description = "사후 인계 - 역할별 패키지 조회 / 행동 완료 / 문제 신고")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posthumous-packages")
public class PosthumousPackageController {

    private final PosthumousPackageService posthumousPackageService;

    @Operation(summary = "패키지 조회", description = "인증된 담당자가 자신의 역할에 필요한 대상·행동·선행 완료 상태만 조회합니다. 다른 역할의 항목과 자격증명 원문은 포함하지 않습니다.")
    @GetMapping("/{accessSessionId}")
    public ResponseEntity<RsData<PosthumousPackageResponse>> getPackage(
            @Parameter(description = "OTP 인증 성공 시 발급된 열람 세션 식별자") @PathVariable String accessSessionId
    ) {
        return ResponseEntity.ok(RsData.success(posthumousPackageService.getPackage(accessSessionId)));
    }

    @Operation(summary = "행동 완료", description = "자신의 역할 항목 하나를 완료 처리합니다. Idempotency-Key 헤더를 보내면 같은 키의 재전송은 중복 요청(409)으로 응답합니다.")
    @PostMapping("/{accessSessionId}/actions/{actionId}/complete")
    public ResponseEntity<RsData<PackageActionResponse>> completeAction(
            @Parameter(description = "열람 세션 식별자") @PathVariable String accessSessionId,
            @Parameter(description = "완료할 행동(항목) ID") @PathVariable Long actionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return ResponseEntity.ok(RsData.success(
                posthumousPackageService.completeAction(accessSessionId, actionId, idempotencyKey)));
    }

    @Operation(summary = "문제 신고", description = "자신의 역할 항목 중 하나에서 발생한 문제를 신고합니다.")
    @PostMapping("/{accessSessionId}/issues")
    public ResponseEntity<RsData<PackageIssueResponse>> reportIssue(
            @Parameter(description = "열람 세션 식별자") @PathVariable String accessSessionId,
            @Valid @RequestBody PackageIssueRequest request
    ) {
        return ResponseEntity.ok(RsData.success(posthumousPackageService.reportIssue(accessSessionId, request)));
    }
}
