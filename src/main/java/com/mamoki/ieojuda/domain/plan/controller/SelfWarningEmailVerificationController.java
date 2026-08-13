package com.mamoki.ieojuda.domain.plan.controller;

import com.mamoki.ieojuda.domain.plan.service.PlanService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "대기 이의제기 설정" 화면 - 검증 메일의 링크를 클릭해서 접근하는 엔드포인트라 로그인이 필요 없다(토큰 자체가 증명).
@Tag(name = "SelfWarningEmail", description = "본인 경고 이메일 검증 (이메일 링크 클릭)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/self-warning-email")
public class SelfWarningEmailVerificationController {

    private final PlanService planService;

    @Operation(summary = "본인 경고 이메일 검증", description = "검증 메일에 담긴 토큰으로 본인 경고 이메일을 확정합니다.")
    @PostMapping("/{token}/verify")
    public ResponseEntity<RsData<Void>> verify(@Parameter(description = "검증 토큰") @PathVariable String token) {
        planService.verifySelfWarningEmail(token);
        return ResponseEntity.ok(RsData.success(null));
    }
}
