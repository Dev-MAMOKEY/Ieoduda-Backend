package com.mamoki.ieojuda.domain.plan.controller;

import com.mamoki.ieojuda.domain.plan.dto.LifeAreaMessageHistoryResponse;
import com.mamoki.ieojuda.domain.plan.dto.LifeAreaMessageRequest;
import com.mamoki.ieojuda.domain.plan.dto.LifeAreaTurnResponse;
import com.mamoki.ieojuda.domain.plan.service.LifeAreaConversationService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "LifeArea Conversation", description = "삶의 구역 작성 - AI와 주고받는 구조화 대화")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plans/{planId}/life-areas/{lifeAreaId}")
public class LifeAreaConversationController {

    private final LifeAreaConversationService lifeAreaConversationService;

    // page=0이 최신 턴, size만큼 과거로 내려가며 조회 (무한 스크롤)
    @Operation(summary = "대화 이력 조회", description = "page=0이 최신 턴이며, size만큼 과거로 내려가며 조회합니다(무한 스크롤). 응답의 messages는 항상 오래된 순으로 정렬됩니다.")
    @GetMapping("/messages")
    public ResponseEntity<RsData<LifeAreaMessageHistoryResponse>> getHistory(
            @Parameter(description = "계획 ID") @PathVariable Long planId,
            @Parameter(description = "삶의 구역 ID") @PathVariable Long lifeAreaId,
            @Parameter(description = "페이지 번호 (0부터 시작, 최신 턴)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size
    ) {
        LifeAreaMessageHistoryResponse history = lifeAreaConversationService
                .getHistory(planId, lifeAreaId, PageRequest.of(page, size));
        return ResponseEntity.ok(RsData.success(history));
    }

    @Operation(summary = "사용자 발화 전송", description = "사용자 발화를 OpenAI에 전달해 다음 턴을 받습니다. AI가 되묻는 중이면 QUESTION, 구조화를 끝냈으면 RESULT(항목 목록)를 반환합니다.")
    @PostMapping("/send/message")
    public ResponseEntity<RsData<LifeAreaTurnResponse>> sendMessage(
            @Parameter(description = "계획 ID") @PathVariable Long planId,
            @Parameter(description = "삶의 구역 ID") @PathVariable Long lifeAreaId,
            @Valid @RequestBody LifeAreaMessageRequest request
    ) {
        LifeAreaTurnResponse result = lifeAreaConversationService.sendMessage(planId, lifeAreaId, request.content());
        return ResponseEntity.ok(RsData.success(result));
    }
}
