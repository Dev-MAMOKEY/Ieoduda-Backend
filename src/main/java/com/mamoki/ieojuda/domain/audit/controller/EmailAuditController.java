package com.mamoki.ieojuda.domain.audit.controller;

import java.util.UUID;

import com.mamoki.ieojuda.domain.audit.dto.EmailDeliveryResponse;
import com.mamoki.ieojuda.domain.audit.dto.ReauthRequest;
import com.mamoki.ieojuda.domain.audit.service.EmailAuditService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 명세서 "이메일 발송 감사" 화면 - 운영관리자 전용
// issue #59 - CASE_SUPERVISE 세부 권한 검사, 사건 동결은 비밀번호 재인증 필요
@Tag(name = "Admin - Email Audit", description = "운영관리자 - 이메일 발송 감사 / 재시도 / 사건 동결")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/release-cases/{caseId}")
public class EmailAuditController {

    private final EmailAuditService emailAuditService;

    @Operation(summary = "이메일 발송 감사 조회", description = "사건에 속한 모든 발송 이력을 조회합니다. 이메일 본문·패키지 내용은 포함하지 않습니다.")
    @GetMapping("/email-deliveries")
    public ResponseEntity<RsData<List<EmailDeliveryResponse>>> getDeliveries(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "사건 ID") @PathVariable UUID caseId
    ) {
        return ResponseEntity.ok(RsData.success(emailAuditService.getDeliveries(userId, caseId)));
    }

    @Operation(summary = "재시도 정책 실행하기", description = "반송·실패한 발송 건에 대해 새 링크를 발급해 재발송합니다.")
    @PostMapping("/email-deliveries/{logId}/retry")
    public ResponseEntity<RsData<EmailDeliveryResponse>> retry(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "사건 ID") @PathVariable UUID caseId,
            @Parameter(description = "발송 로그 ID") @PathVariable UUID logId
    ) {
        return ResponseEntity.ok(RsData.success(emailAuditService.retry(userId, caseId, logId)));
    }

    @Operation(summary = "사건 동결하기", description = "이 사건의 이후 발송·단계 전환을 전부 중지합니다. 되돌리기 어려운 조작이라 비밀번호 재확인이 필요합니다.")
    @PostMapping("/freeze")
    public ResponseEntity<RsData<Void>> freeze(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "사건 ID") @PathVariable UUID caseId,
            @Valid @RequestBody ReauthRequest request
    ) {
        emailAuditService.freeze(userId, caseId, request.password());
        return ResponseEntity.ok(RsData.success(null));
    }

    @Operation(summary = "사건 동결 해제하기", description = "동결을 해제합니다. 막혀 있던 경고 발송·상태 전이를 다시 시도하려면 '경고 발송 재시도'를 별도로 호출해야 합니다.")
    @PostMapping("/unfreeze")
    public ResponseEntity<RsData<Void>> unfreeze(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "사건 ID") @PathVariable UUID caseId,
            @Valid @RequestBody ReauthRequest request
    ) {
        emailAuditService.unfreeze(userId, caseId, request.password());
        return ResponseEntity.ok(RsData.success(null));
    }

    @Operation(summary = "경고 발송 재시도", description = "취소·이의 제기 경고 메일 발송 실패로 동결된 사건에서, 막혀 있던 발송과 그 발송이 게이트하던 상태 전이를 다시 시도합니다.")
    @PostMapping("/retry-warning")
    public ResponseEntity<RsData<Void>> retryWarning(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "사건 ID") @PathVariable UUID caseId,
            @Valid @RequestBody ReauthRequest request
    ) {
        emailAuditService.retryWarning(userId, caseId, request.password());
        return ResponseEntity.ok(RsData.success(null));
    }
}
