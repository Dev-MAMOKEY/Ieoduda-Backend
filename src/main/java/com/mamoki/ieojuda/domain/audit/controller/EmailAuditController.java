package com.mamoki.ieojuda.domain.audit.controller;

import com.mamoki.ieojuda.domain.audit.dto.EmailDeliveryResponse;
import com.mamoki.ieojuda.domain.audit.service.EmailAuditService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 명세서 "이메일 발송 감사" 화면 - 운영관리자 전용
@Tag(name = "Admin - Email Audit", description = "운영관리자 - 이메일 발송 감사 / 재시도 / 사건 동결")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/release-cases/{caseId}")
public class EmailAuditController {

    private final EmailAuditService emailAuditService;

    @Operation(summary = "이메일 발송 감사 조회", description = "사건에 속한 모든 발송 이력을 조회합니다. 이메일 본문·패키지 내용은 포함하지 않습니다.")
    @GetMapping("/email-deliveries")
    public ResponseEntity<RsData<List<EmailDeliveryResponse>>> getDeliveries(
            @Parameter(description = "사건 ID") @PathVariable Long caseId
    ) {
        return ResponseEntity.ok(RsData.success(emailAuditService.getDeliveries(caseId)));
    }

    @Operation(summary = "재시도 정책 실행하기", description = "반송·실패한 발송 건에 대해 새 링크를 발급해 재발송합니다.")
    @PostMapping("/email-deliveries/{logId}/retry")
    public ResponseEntity<RsData<EmailDeliveryResponse>> retry(
            @Parameter(description = "사건 ID") @PathVariable Long caseId,
            @Parameter(description = "발송 로그 ID") @PathVariable Long logId
    ) {
        return ResponseEntity.ok(RsData.success(emailAuditService.retry(caseId, logId)));
    }

    @Operation(summary = "사건 동결하기", description = "이 사건의 이후 발송·단계 전환을 전부 중지합니다.")
    @PostMapping("/freeze")
    public ResponseEntity<RsData<Void>> freeze(
            @Parameter(description = "사건 ID") @PathVariable Long caseId
    ) {
        emailAuditService.freeze(caseId);
        return ResponseEntity.ok(RsData.success(null));
    }

    @Operation(summary = "사건 동결 해제하기", description = "동결된 사건을 다시 정상 상태로 되돌립니다.")
    @PostMapping("/unfreeze")
    public ResponseEntity<RsData<Void>> unfreeze(
            @Parameter(description = "사건 ID") @PathVariable Long caseId
    ) {
        emailAuditService.unfreeze(caseId);
        return ResponseEntity.ok(RsData.success(null));
    }
}
