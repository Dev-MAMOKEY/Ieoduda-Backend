package com.mamoki.ieojuda.domain.recipient.controller;

import com.mamoki.ieojuda.domain.recipient.dto.RecipientBulkRegisterRequest;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientBulkRegisterResponse;
import com.mamoki.ieojuda.domain.recipient.service.RecipientService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "역할 담당자 등록" 화면 - 승인된 항목(박스)마다 담당자를 배정하고 수락 이메일을 발송
@Tag(name = "Recipient", description = "역할 담당자 등록 / 수락 이메일 발송")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plans/{planId}/recipients")
public class RecipientController {

    private final RecipientService recipientService;

    @Operation(summary = "역할 담당자 일괄 등록", description = "승인된 항목 개수만큼 담당자 정보를 한 번에 등록하고, 등록 즉시 역할 수락 이메일을 발송합니다. 각 담당자는 대체 담당자(backup)를 선택적으로 1명 함께 등록할 수 있으며, 등록 시 대체 담당자에게도 수락 이메일이 발송됩니다. 일부 발송이 실패해도 담당자 저장은 유지되며 건별 발송 결과가 응답에 담깁니다.")
    @PostMapping
    public ResponseEntity<RsData<RecipientBulkRegisterResponse>> registerAll(
            @Parameter(description = "계획 ID") @PathVariable Long planId,
            @Valid @RequestBody RecipientBulkRegisterRequest request
    ) {
        RecipientBulkRegisterResponse result = recipientService.registerAll(planId, request);
        return ResponseEntity.ok(RsData.success(result));
    }
}
