package com.mamoki.ieojuda.domain.handoffcheck.controller;

import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckRespondRequest;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckRespondResponse;
import com.mamoki.ieojuda.domain.handoffcheck.service.HandoffCheckRespondService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "선택형 생전 인계 점검" - 담당자가 점검 메일의 링크로 접근하는 엔드포인트라 로그인이 필요 없다(토큰 자체가 증명)
@Tag(name = "HandoffCheckRespond", description = "선택형 생전 인계 점검 - 담당자 응답 (이메일 링크 클릭)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/handoff-checks")
public class HandoffCheckRespondController {

    private final HandoffCheckRespondService handoffCheckRespondService;

    @Operation(summary = "인계 점검 응답", description = "담당자가 이메일 도달·역할 이해·공개 범주 이해 여부와 문의 사항을 응답합니다. 만료·재사용 링크는 차단됩니다.")
    @PostMapping("/{token}/respond")
    public ResponseEntity<RsData<HandoffCheckRespondResponse>> respond(
            @Parameter(description = "점검 토큰") @PathVariable String token,
            @Valid @RequestBody HandoffCheckRespondRequest request
    ) {
        return ResponseEntity.ok(RsData.success(handoffCheckRespondService.respond(token, request)));
    }
}
