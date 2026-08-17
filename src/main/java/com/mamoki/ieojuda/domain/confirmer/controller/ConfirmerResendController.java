package com.mamoki.ieojuda.domain.confirmer.controller;

import java.util.UUID;

import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerResendResponse;
import com.mamoki.ieojuda.domain.confirmer.service.ConfirmerService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "지정확인자 등록" 화면 - "수락 요청 다시 보내기" 버튼
@Tag(name = "Confirmer", description = "지정 확인자 등록 / 수락 이메일 발송")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/confirmers")
public class ConfirmerResendController {

    private final ConfirmerService confirmerService;

    @Operation(summary = "지정 확인자 수락 요청 다시 보내기", description = "만료되었거나 아직 응답하지 않은 확인자에게 수락 이메일을 재발송합니다. 이미 수락하거나 거절한 확인자에게는 재발송할 수 없습니다.")
    @PostMapping("/{confirmId}/acceptance-email")
    public ResponseEntity<RsData<ConfirmerResendResponse>> resendAcceptanceEmail(
            @AuthenticationPrincipal UUID userId, // 현재 로그인한 사용자 ID 식별
            @Parameter(description = "확인자 ID") @PathVariable UUID confirmId
    ) {
        ConfirmerResendResponse result = confirmerService.resendAcceptanceEmail(userId, confirmId);
        return ResponseEntity.ok(RsData.success(result));
    }
}
