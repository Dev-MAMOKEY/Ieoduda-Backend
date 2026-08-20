package com.mamoki.ieojuda.domain.recipient.controller;

import java.util.UUID;

import com.mamoki.ieojuda.domain.recipient.dto.RecipientAcceptanceEmailResponse;
import com.mamoki.ieojuda.domain.recipient.service.RecipientService;
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

// 명세서 "역할 담당자 등록" 화면 - "수락 요청 다시 보내기" 버튼
@Tag(name = "Recipient", description = "역할 담당자 등록 / 수락 이메일 발송")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recipients")
public class RecipientAcceptanceEmailController {

    private final RecipientService recipientService;

    @Operation(summary = "역할 수락 요청 다시 보내기", description = "만료되었거나 아직 응답하지 않은 담당자에게 수락 이메일을 재발송합니다. 이미 수락하거나 거절한 담당자에게는 재발송할 수 없습니다.")
    @PostMapping("/{recipientId}/acceptance-email")
    public ResponseEntity<RsData<RecipientAcceptanceEmailResponse>> resendAcceptanceEmail(
            @AuthenticationPrincipal UUID userId, // 현재 로그인한 사용자 ID 식별
            @Parameter(description = "담당자 ID") @PathVariable UUID recipientId
    ) {
        RecipientAcceptanceEmailResponse result = recipientService.resendAcceptanceEmail(userId, recipientId);
        return ResponseEntity.ok(RsData.success(result));
    }
}
