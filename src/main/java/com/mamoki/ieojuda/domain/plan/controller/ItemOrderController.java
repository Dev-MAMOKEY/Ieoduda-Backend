package com.mamoki.ieojuda.domain.plan.controller;

import java.util.UUID;

import com.mamoki.ieojuda.domain.plan.dto.ItemReorderRequest;
import com.mamoki.ieojuda.domain.plan.dto.OrderCheckResponse;
import com.mamoki.ieojuda.domain.plan.dto.OrderConfirmResponse;
import com.mamoki.ieojuda.domain.plan.service.ItemOrderService;
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

// 명세서 "실행 순서 점검" 화면 - 담당자가 배정된 항목의 실행 순서 조회/변경/확정
@Tag(name = "Item Order", description = "실행 순서 점검 - 조회 / 드래그 순서 변경 / 확정")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plans/{planId}/items/order")
public class ItemOrderController {

    private final ItemOrderService itemOrderService;

    @Operation(summary = "실행 순서 점검 목록 조회", description = "담당자가 배정된 항목을 실행 순서대로 조회합니다. 삭제형 항목이 인계형 항목보다 먼저 오면 충돌로 표시됩니다.")
    @GetMapping
    public ResponseEntity<RsData<OrderCheckResponse>> getOrderCheck(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "계획 ID") @PathVariable UUID planId
    ) {
        return ResponseEntity.ok(RsData.success(itemOrderService.getOrderCheck(userId, planId)));
    }

    @Operation(summary = "실행 순서 변경", description = "드래그로 바뀐 새 순서 전체를 저장합니다. 순서 점검 대상 항목 전체가 빠짐없이 포함되어야 합니다. 저장 시 기존 확정 상태는 초기화됩니다.")
    @PutMapping
    public ResponseEntity<RsData<OrderCheckResponse>> reorder(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "계획 ID") @PathVariable UUID planId,
            @Valid @RequestBody ItemReorderRequest request
    ) {
        return ResponseEntity.ok(RsData.success(itemOrderService.reorder(userId, planId, request)));
    }

    @Operation(summary = "순서 확정하기", description = "충돌이 하나라도 남아있으면 확정할 수 없습니다.")
    @PostMapping("/confirm")
    public ResponseEntity<RsData<OrderConfirmResponse>> confirm(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "계획 ID") @PathVariable UUID planId
    ) {
        return ResponseEntity.ok(RsData.success(itemOrderService.confirm(userId, planId)));
    }
}
