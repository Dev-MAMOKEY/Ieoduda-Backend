package com.mamoki.ieojuda.domain.plan.controller;

import com.mamoki.ieojuda.domain.plan.dto.LifeAreaMessageHistoryResponse;
import com.mamoki.ieojuda.domain.plan.dto.LifeAreaMessageRequest;
import com.mamoki.ieojuda.domain.plan.dto.LifeAreaTurnResponse;
import com.mamoki.ieojuda.domain.plan.service.LifeAreaConversationService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "삶의 구역 작성" 화면 - 대화(말풍선) 작성/조회
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plans/{planId}/life-areas/{lifeAreaId}")
public class LifeAreaConversationController {

    private final LifeAreaConversationService lifeAreaConversationService;

    // page=0이 최신 턴, size만큼 과거로 내려가며 조회 (무한 스크롤)
    @GetMapping("/messages")
    public ResponseEntity<RsData<LifeAreaMessageHistoryResponse>> getHistory(
            @PathVariable Long planId,
            @PathVariable Long lifeAreaId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        LifeAreaMessageHistoryResponse history = lifeAreaConversationService
                .getHistory(planId, lifeAreaId, PageRequest.of(page, size));
        return ResponseEntity.ok(RsData.success(history));
    }

    @PostMapping("/send/message")
    public ResponseEntity<RsData<LifeAreaTurnResponse>> sendMessage(
            @PathVariable Long planId,
            @PathVariable Long lifeAreaId,
            @Valid @RequestBody LifeAreaMessageRequest request
    ) {
        LifeAreaTurnResponse result = lifeAreaConversationService.sendMessage(planId, lifeAreaId, request.content());
        return ResponseEntity.ok(RsData.success(result));
    }
}
