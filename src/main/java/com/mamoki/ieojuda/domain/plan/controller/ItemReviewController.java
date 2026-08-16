package com.mamoki.ieojuda.domain.plan.controller;

import com.mamoki.ieojuda.domain.plan.dto.ItemReviewRequest;
import com.mamoki.ieojuda.domain.plan.dto.ItemResponse;
import com.mamoki.ieojuda.domain.plan.dto.ItemUpdateRequest;
import com.mamoki.ieojuda.domain.plan.service.ItemReviewService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "AI 구조화 결과 검토" 화면 - 항목 조회/승인/수정/삭제
@Tag(name = "Item Review", description = "AI 구조화 결과 검토 - 항목 조회/승인/수정/삭제")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plans/{planId}/items")
public class ItemReviewController {

    private final ItemReviewService itemReviewService;

    @Operation(summary = "항목 단건 조회")
    @GetMapping("/{itemId}")
    public ResponseEntity<RsData<ItemResponse>> getItem(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "계획 ID") @PathVariable Long planId,
            @Parameter(description = "항목 ID") @PathVariable Long itemId
    ) {
        return ResponseEntity.ok(RsData.success(itemReviewService.getItem(userId, planId, itemId)));
    }

    @Operation(summary = "항목 승인", description = "AI가 만든 항목을 승인합니다. 원문 근거가 없는 항목은 승인할 수 없습니다.")
    @PostMapping("/review")
    public ResponseEntity<RsData<ItemResponse>> approve(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "계획 ID") @PathVariable Long planId,
            @Valid @RequestBody ItemReviewRequest request
    ) {
        ItemResponse result = itemReviewService.approve(userId, planId, request);
        return ResponseEntity.ok(RsData.success(result));
    }

    @Operation(summary = "항목 인라인 수정", description = "대화창에서 AI가 만든 항목을 화면 전환 없이 사용자가 직접 고쳐서 저장합니다.")
    @PutMapping("/{itemId}")
    public ResponseEntity<RsData<ItemResponse>> update(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "계획 ID") @PathVariable Long planId,
            @Parameter(description = "항목 ID") @PathVariable Long itemId,
            @Valid @RequestBody ItemUpdateRequest request
    ) {
        ItemResponse result = itemReviewService.update(userId, planId, itemId, request);
        return ResponseEntity.ok(RsData.success(result));
    }

    @Operation(summary = "항목 삭제", description = "AI가 만든 항목을 완전히 삭제합니다(상태 변경이 아닌 실제 삭제).")
    @DeleteMapping("/{itemId}")
    public ResponseEntity<RsData<Void>> delete(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "계획 ID") @PathVariable Long planId,
            @Parameter(description = "항목 ID") @PathVariable Long itemId
    ) {
        itemReviewService.delete(userId, planId, itemId);
        return ResponseEntity.ok(RsData.success(null));
    }
}
