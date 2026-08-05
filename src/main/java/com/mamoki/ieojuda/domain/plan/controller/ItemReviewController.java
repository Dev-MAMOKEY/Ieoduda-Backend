package com.mamoki.ieojuda.domain.plan.controller;

import com.mamoki.ieojuda.domain.plan.dto.ItemReviewRequest;
import com.mamoki.ieojuda.domain.plan.dto.LifeAreaTurnResponse;
import com.mamoki.ieojuda.domain.plan.service.ItemReviewService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "AI 구조화 결과 검토" 화면 - 항목 승인/기각
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plans/{planId}/items")
public class ItemReviewController {

    private final ItemReviewService itemReviewService;

    @PostMapping("/review")
    public ResponseEntity<RsData<LifeAreaTurnResponse.ItemResponse>> review(
            @PathVariable Long planId,
            @Valid @RequestBody ItemReviewRequest request
    ) {
        LifeAreaTurnResponse.ItemResponse result = itemReviewService.review(planId, request);
        return ResponseEntity.ok(RsData.success(result));
    }
}
