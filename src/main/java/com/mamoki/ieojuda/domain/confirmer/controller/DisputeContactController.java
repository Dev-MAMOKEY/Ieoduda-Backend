package com.mamoki.ieojuda.domain.confirmer.controller;

import com.mamoki.ieojuda.domain.confirmer.dto.DisputeContactRegisterRequest;
import com.mamoki.ieojuda.domain.confirmer.dto.DisputeContactResponse;
import com.mamoki.ieojuda.domain.confirmer.service.DisputeContactService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "대기 이의제기 설정" 화면 - 이의 제기 연락처 등록 / 검증
@Tag(name = "DisputeContact", description = "이의 제기 연락처 등록 / 이메일 검증")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class DisputeContactController {

    private final DisputeContactService disputeContactService;

    @Operation(summary = "이의 제기 연락처 등록", description = "이의 제기 연락처를 등록하고 그 주소로 검증 메일을 발송합니다.")
    @PostMapping("/plans/{planId}/dispute-contacts")
    public ResponseEntity<RsData<DisputeContactResponse>> register(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "계획 ID") @PathVariable Long planId,
            @Valid @RequestBody DisputeContactRegisterRequest request
    ) {
        return ResponseEntity.ok(RsData.success(disputeContactService.register(userId, planId, request)));
    }

    @Operation(summary = "이의 제기 연락처 수정", description = "이름/이메일을 수정합니다. 이메일이 바뀌면 검증 상태가 초기화되고 새 검증 메일이 발송됩니다.")
    @PutMapping("/plans/{planId}/dispute-contacts/{contactId}")
    public ResponseEntity<RsData<DisputeContactResponse>> update(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "계획 ID") @PathVariable Long planId,
            @Parameter(description = "이의 제기 연락처 ID") @PathVariable Long contactId,
            @Valid @RequestBody DisputeContactRegisterRequest request
    ) {
        return ResponseEntity.ok(RsData.success(disputeContactService.update(userId, planId, contactId, request)));
    }

    // 검증 메일의 링크를 클릭해서 접근하는 엔드포인트라 로그인이 필요 없다(토큰 자체가 증명)
    @Operation(summary = "이의 제기 연락처 검증", description = "검증 메일에 담긴 토큰으로 이의 제기 연락처 등록을 확정합니다.")
    @PostMapping("/dispute-contacts/{token}/verify")
    public ResponseEntity<RsData<Void>> verify(@Parameter(description = "검증 토큰") @PathVariable String token) {
        disputeContactService.verify(token);
        return ResponseEntity.ok(RsData.success(null));
    }
}
