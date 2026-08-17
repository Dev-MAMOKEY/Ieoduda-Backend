package com.mamoki.ieojuda.domain.confirmer.controller;

import java.util.UUID;

import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerBulkRegisterRequest;
import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerBulkRegisterResponse;
import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerDetailResponse;
import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerUpdateRequest;
import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerUpdateResponse;
import com.mamoki.ieojuda.domain.confirmer.service.ConfirmerService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
            @AuthenticationPrincipal UUID userId, // 현재 로그인한 사용자 ID 식별
            @Parameter(description = "계획 ID") @PathVariable UUID planId,
            @Valid @RequestBody ConfirmerBulkRegisterRequest request
    ) {
        ConfirmerBulkRegisterResponse result = confirmerService.registerAll(userId, planId, request);
        return ResponseEntity.ok(RsData.success(result));
    }

    // 명세서 "역할 점검" 화면 - 이름 클릭 시 해당 확인자 상세
    @Operation(summary = "지정 확인자 상세 조회", description = "이름 클릭 시 해당 확인자의 이름, 이메일, 수락 상태를 조회합니다.")
    @GetMapping("/{confirmId}")
    public ResponseEntity<RsData<ConfirmerDetailResponse>> getConfirmer(
            @AuthenticationPrincipal UUID userId, // 현재 로그인한 사용자 ID 식별
            @Parameter(description = "계획 ID") @PathVariable UUID planId,
            @Parameter(description = "확인자 ID") @PathVariable UUID confirmId
    ) {
        ConfirmerDetailResponse result = confirmerService.getConfirmer(userId, planId, confirmId);
        return ResponseEntity.ok(RsData.success(result));
    }

    // 명세서 "지정확인자 수정" 화면 - 이름/이메일 수정, 이메일이 바뀌면 수락 이메일 재발송
    @Operation(summary = "지정 확인자 수정", description = "확인자 이름/이메일을 수정합니다. 이메일이 바뀌면 수락 상태가 초기화되고 새 수락 이메일이 발송됩니다.")
    @PutMapping("/{confirmId}")
    public ResponseEntity<RsData<ConfirmerUpdateResponse>> updateConfirmer(
            @AuthenticationPrincipal UUID userId, // 현재 로그인한 사용자 ID 식별
            @Parameter(description = "계획 ID") @PathVariable UUID planId,
            @Parameter(description = "확인자 ID") @PathVariable UUID confirmId,
            @Valid @RequestBody ConfirmerUpdateRequest request
    ) {
        ConfirmerUpdateResponse result = confirmerService.updateConfirmer(userId, planId, confirmId, request);
        return ResponseEntity.ok(RsData.success(result));
    }
}
