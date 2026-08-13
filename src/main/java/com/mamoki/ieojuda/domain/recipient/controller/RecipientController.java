package com.mamoki.ieojuda.domain.recipient.controller;

import com.mamoki.ieojuda.domain.recipient.dto.RecipientBulkRegisterRequest;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientBulkRegisterResponse;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientDetailResponse;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientSummaryResponse;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientUpdateRequest;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientUpdateResponse;
import com.mamoki.ieojuda.domain.recipient.service.RecipientService;
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

import java.util.List;

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

    // 명세서 "역할 점검" 화면 - 상단 이름 목록 (row 단위, 같은 사람이 여러 항목을 맡으면 이름이 중복될 수 있음)
    @Operation(summary = "역할 담당자 이름 목록 조회", description = "역할 점검 화면 상단에 표시할 담당자 이름 목록을 조회합니다. 대체 담당자는 포함하지 않습니다.")
    @GetMapping
    public ResponseEntity<RsData<List<RecipientSummaryResponse>>> getRecipients(
            @AuthenticationPrincipal Long userId, // 현재 로그인한 사용자 ID 식별
            @Parameter(description = "계획 ID") @PathVariable Long planId
    ) {
        List<RecipientSummaryResponse> result = recipientService.getRecipients(userId, planId);
        return ResponseEntity.ok(RsData.success(result));
    }

    // 명세서 "역할 점검" 화면 - 이름 클릭 시 해당 담당자의 역할 상세
    @Operation(summary = "역할 담당자 상세 조회", description = "이름 클릭 시 해당 담당자에게 배정된 항목 전체(제목/내용/행동 등)를 조회합니다.")
    @GetMapping("/{assigneeId}")
    public ResponseEntity<RsData<RecipientDetailResponse>> getRecipient(
            @AuthenticationPrincipal Long userId, // 현재 로그인한 사용자 ID 식별
            @Parameter(description = "계획 ID") @PathVariable Long planId,
            @Parameter(description = "담당자 ID") @PathVariable Long assigneeId
    ) {
        RecipientDetailResponse result = recipientService.getRecipient(userId, planId, assigneeId);
        return ResponseEntity.ok(RsData.success(result));
    }

    // 명세서 "역할 담당자 수정" 화면 - 이름/이메일 수정, 이메일이 바뀌면 수락 이메일 재발송
    @Operation(summary = "역할 담당자 수정", description = "담당자 이름/이메일을 수정합니다. 이메일이 바뀌면 수락 상태가 초기화되고 새 수락 이메일이 발송됩니다.")
    @PutMapping("/{assigneeId}")
    public ResponseEntity<RsData<RecipientUpdateResponse>> updateRecipient(
            @AuthenticationPrincipal Long userId, // 현재 로그인한 사용자 ID 식별
            @Parameter(description = "계획 ID") @PathVariable Long planId,
            @Parameter(description = "담당자 ID") @PathVariable Long assigneeId,
            @Valid @RequestBody RecipientUpdateRequest request
    ) {
        RecipientUpdateResponse result = recipientService.updateRecipient(userId, planId, assigneeId, request);
        return ResponseEntity.ok(RsData.success(result));
    }
}
