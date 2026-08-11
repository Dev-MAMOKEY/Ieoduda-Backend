package com.mamoki.ieojuda.domain.plan.controller;

import com.mamoki.ieojuda.domain.plan.dto.ConversationResponse;
import com.mamoki.ieojuda.domain.plan.dto.LifeAreaMessageHistoryResponse;
import com.mamoki.ieojuda.domain.plan.dto.LifeAreaMessageRequest;
import com.mamoki.ieojuda.domain.plan.dto.LifeAreaTurnResponse;
import com.mamoki.ieojuda.domain.plan.service.ConversationService;
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

// 명세서 "삶의 구역 생성" 화면 - AI와 주고받는 구조화 대화. "계획 만들기" 폼이 없어졌으므로 첫 메세지부터 여기서 처리한다.
// 대화는 세션(Conversation) 단위로 나뉜다 - 처음 채팅한 세션과 나중에 수정하러 들어온 세션을 구분해서 기록하기 위함.
@Tag(name = "Conversation", description = "삶의 구역 생성 - AI와 주고받는 구조화 대화 세션")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plans/{planId}/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    @Operation(summary = "대화 세션 시작", description = "새 대화 세션을 시작합니다. 처음 채팅을 시작할 때, 또는 나중에 수정하러 다시 들어올 때마다 호출합니다.")
    @PostMapping
    public ResponseEntity<RsData<ConversationResponse>> start(
            @Parameter(description = "계획 ID") @PathVariable Long planId
    ) {
        return ResponseEntity.ok(RsData.success(conversationService.startConversation(planId)));
    }

    // page=0이 최신 턴, size만큼 과거로 내려가며 조회 (무한 스크롤)
    @Operation(summary = "대화 이력 조회", description = "page=0이 최신 턴이며, size만큼 과거로 내려가며 조회합니다(무한 스크롤). 응답의 messages는 항상 오래된 순으로 정렬됩니다.")
    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<RsData<LifeAreaMessageHistoryResponse>> getHistory(
            @Parameter(description = "계획 ID") @PathVariable Long planId,
            @Parameter(description = "대화 세션 ID") @PathVariable Long conversationId,
            @Parameter(description = "페이지 번호 (0부터 시작, 최신 턴)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size
    ) {
        LifeAreaMessageHistoryResponse history = conversationService
                .getHistory(planId, conversationId, PageRequest.of(page, size));
        return ResponseEntity.ok(RsData.success(history));
    }

    @Operation(summary = "사용자 발화 전송", description = "사용자 발화를 OpenAI에 전달해 다음 턴을 받습니다. AI가 되묻는 중이면 QUESTION, 구조화를 끝냈으면 RESULT(항목 목록)를 반환합니다. 한 발화에 여러 사람·주제가 섞여 있으면 항목이 여러 카테고리로 나뉘어 반환될 수 있습니다.")
    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<RsData<LifeAreaTurnResponse>> sendMessage(
            @Parameter(description = "계획 ID") @PathVariable Long planId,
            @Parameter(description = "대화 세션 ID") @PathVariable Long conversationId,
            @Valid @RequestBody LifeAreaMessageRequest request
    ) {
        LifeAreaTurnResponse result = conversationService.sendMessage(planId, conversationId, request.content());
        return ResponseEntity.ok(RsData.success(result));
    }
}
