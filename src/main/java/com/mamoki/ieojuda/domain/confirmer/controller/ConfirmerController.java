package com.mamoki.ieojuda.domain.confirmer.controller;

import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerBulkRegisterRequest;
import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerBulkRegisterResponse;
import com.mamoki.ieojuda.domain.confirmer.service.ConfirmerService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// "지정확인자 등록" 화면 - 확인자를 한 번에 등록하고 수락 이메일을 발송
@Tag(name = "Confirmer", description = "지정 확인자 등록 / 수락 이메일 발송")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plans/{planId}/confirmers")
public class ConfirmerController {

    private final ConfirmerService confirmerService;

    @Operation(summary = "지정 확인자 일괄 등록", description = "입력한 확인자 정보를 한 번에 등록하고, 등록 즉시 수락 이메일을 발송합니다. 일부 발송이 실패해도 확인자 저장은 유지되며 건별 발송 결과가 응답에 담깁니다.")
    @PostMapping
    public ResponseEntity<RsData<ConfirmerBulkRegisterResponse>> registerAll(
            @AuthenticationPrincipal Long userId, // 현재 로그인한 사용자 ID 식별
            @Parameter(description = "계획 ID") @PathVariable Long planId,
            @Valid @RequestBody ConfirmerBulkRegisterRequest request
    ) {
        ConfirmerBulkRegisterResponse result = confirmerService.registerAll(userId, planId, request);
        return ResponseEntity.ok(RsData.success(result));
    }
}
