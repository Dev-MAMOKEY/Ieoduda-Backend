package com.mamoki.ieojuda.domain.postaccess.controller;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "역할별 사후 패키지" 화면 - OTP 확인을 통과한 접근 세션이 곧 인증 수단이라 로그인이 필요 없다
@Tag(name = "PosthumousPackage", description = "역할별 사후 패키지 조회 / 행동 완료 / 문제 신고")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posthumous-packages")
public class PosthumousPackageController {

    private final PosthumousPackageService posthumousPackageService;

    @Operation(summary = "사후 패키지 조회", description = "본인에게 배정된 항목만 실행 순서대로 조회합니다. 다른 역할의 항목은 포함하지 않습니다.")
    @GetMapping("/{accessSessionId}")
    public ResponseEntity<RsData<PosthumousPackageResponse>> getPackage(
            @Parameter(description = "OTP 확인으로 받은 접근 세션 ID") @PathVariable Long accessSessionId
    ) {
        return ResponseEntity.ok(RsData.success(posthumousPackageService.getPackage(accessSessionId)));
    }

    @Operation(summary = "행동 완료", description = "화면의 '완료하기' - 해당 항목을 완료 처리하고 갱신된 패키지를 반환합니다.")
    @PostMapping("/{accessSessionId}/actions/{itemId}/complete")
    public ResponseEntity<RsData<PosthumousPackageResponse>> completeAction(
            @Parameter(description = "OTP 확인으로 받은 접근 세션 ID") @PathVariable Long accessSessionId,
            @Parameter(description = "완료 처리할 항목 ID") @PathVariable Long itemId
    ) {
        return ResponseEntity.ok(RsData.success(posthumousPackageService.completeAction(accessSessionId, itemId)));
    }

    @Operation(summary = "문제 신고", description = "화면의 '문제 신고하기' - 신고 내용을 접수합니다. 실제 대체 담당자 전환은 운영자가 별도로 처리합니다.")
    @PostMapping("/{accessSessionId}/issues")
    public ResponseEntity<RsData<PackageIssueResponse>> reportIssue(
            @Parameter(description = "OTP 확인으로 받은 접근 세션 ID") @PathVariable Long accessSessionId,
            @Valid @RequestBody PackageIssueRequest request
    ) {
        return ResponseEntity.ok(RsData.success(posthumousPackageService.reportIssue(accessSessionId, request)));
    }
}
