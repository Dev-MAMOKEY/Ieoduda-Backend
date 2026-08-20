package com.mamoki.ieojuda.domain.confirmer.controller;

import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerDecisionRequest;
import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerDecisionResponse;
import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerInviteResponse;
import com.mamoki.ieojuda.domain.confirmer.service.ConfirmerInviteService;
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

// 명세서 "지정확인자 수락 이메일/화면" - 확인자가 초대 이메일의 링크로 접근하는 엔드포인트라 로그인이 필요 없다(토큰 자체가 증명)
@Tag(name = "ConfirmerInvite", description = "지정 확인자 - 초대 조회 / 수락 / 거절 (이메일 링크 클릭)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/confirmer-acceptances")
public class ConfirmerInviteController {

    private final ConfirmerInviteService confirmerInviteService;

    @Operation(summary = "초대 조회", description = "지정확인자 수락 이메일의 링크로 진입했을 때 확인자 이름, 작성자 이름, 발송된 이메일 주소, 만료 시각, 문의 주소를 조회합니다. 만료된 링크는 차단됩니다.")
    @GetMapping("/{token}")
    public ResponseEntity<RsData<ConfirmerInviteResponse>> getInvite(
            @Parameter(description = "초대 토큰") @PathVariable String token
    ) {
        return ResponseEntity.ok(RsData.success(confirmerInviteService.getInvite(token)));
    }

    // 문의 사항은 선택 입력이라 바디 자체를 생략해도 받아야 하므로 required = false
    @Operation(summary = "역할 수락", description = "확인자가 역할을 수락합니다. 문의 사항은 선택 입력이라 요청 바디를 생략할 수 있습니다. 만료·재사용 링크는 차단됩니다.")
    @PostMapping("/{token}/accept")
    public ResponseEntity<RsData<ConfirmerDecisionResponse>> accept(
            @Parameter(description = "초대 토큰") @PathVariable String token,
            @Valid @RequestBody(required = false) ConfirmerDecisionRequest request
    ) {
        return ResponseEntity.ok(RsData.success(confirmerInviteService.accept(token, request)));
    }

    @Operation(summary = "역할 거절", description = "확인자가 역할을 거절합니다. 문의 사항은 선택 입력이라 요청 바디를 생략할 수 있습니다. 만료·재사용 링크는 차단됩니다.")
    @PostMapping("/{token}/decline")
    public ResponseEntity<RsData<ConfirmerDecisionResponse>> decline(
            @Parameter(description = "초대 토큰") @PathVariable String token,
            @Valid @RequestBody(required = false) ConfirmerDecisionRequest request
    ) {
        return ResponseEntity.ok(RsData.success(confirmerInviteService.decline(token, request)));
    }
}
