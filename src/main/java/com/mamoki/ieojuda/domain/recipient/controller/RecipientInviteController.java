package com.mamoki.ieojuda.domain.recipient.controller;

import com.mamoki.ieojuda.domain.recipient.dto.RecipientInviteDecisionRequest;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientInviteDecisionResponse;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientInviteResponse;
import com.mamoki.ieojuda.domain.recipient.service.RecipientInviteService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "역할 수락 이메일/화면" - 담당자가 초대 이메일의 링크로 접근하는 엔드포인트라 로그인이 필요 없다(토큰 자체가 증명)
@Tag(name = "RecipientInvite", description = "역할 담당자 - 초대 조회 / 수락 / 거절 (이메일 링크 클릭)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recipient-acceptances")
public class RecipientInviteController {

    private final RecipientInviteService recipientInviteService;

    @Operation(summary = "초대 조회", description = "역할 수락 이메일의 링크로 진입했을 때 작성자 이름, 역할명, 할 일 목록, 공개 범주를 조회합니다. 만료된 링크는 차단됩니다.")
    @GetMapping("/{token}")
    public ResponseEntity<RsData<RecipientInviteResponse>> getInvite(
            @Parameter(description = "초대 토큰") @PathVariable String token
    ) {
        return ResponseEntity.ok(RsData.success(recipientInviteService.getInvite(token)));
    }

    // 문의 사항은 선택 입력이라 바디 자체를 생략해도 받아야 하므로 required = false
    @Operation(summary = "역할 수락", description = "담당자가 역할을 수락합니다. 문의 사항은 선택 입력이라 요청 바디를 생략할 수 있습니다. 만료·재사용 링크는 차단됩니다.")
    @PostMapping("/{token}/accept")
    public ResponseEntity<RsData<RecipientInviteDecisionResponse>> accept(
            @Parameter(description = "초대 토큰") @PathVariable String token,
            @Valid @RequestBody(required = false) RecipientInviteDecisionRequest request
    ) {
        return ResponseEntity.ok(RsData.success(recipientInviteService.accept(token, request)));
    }

    @Operation(summary = "역할 거절", description = "담당자가 역할을 거절합니다. 문의 사항은 선택 입력이라 요청 바디를 생략할 수 있습니다. 만료·재사용 링크는 차단됩니다.")
    @PostMapping("/{token}/decline")
    public ResponseEntity<RsData<RecipientInviteDecisionResponse>> decline(
            @Parameter(description = "초대 토큰") @PathVariable String token,
            @Valid @RequestBody(required = false) RecipientInviteDecisionRequest request
    ) {
        return ResponseEntity.ok(RsData.success(recipientInviteService.decline(token, request)));
    }
}
