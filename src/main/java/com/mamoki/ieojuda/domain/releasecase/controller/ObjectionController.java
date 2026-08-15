package com.mamoki.ieojuda.domain.releasecase.controller;

import com.mamoki.ieojuda.domain.releasecase.dto.ObjectionRequest;
import com.mamoki.ieojuda.domain.releasecase.dto.ObjectionResponse;
import com.mamoki.ieojuda.domain.releasecase.service.ObjectionService;
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

// 명세서 "사후 인계" 화면 - 이의 제기 연락처가 이의를 접수 (로그인 불필요, 초대 토큰이 곧 인증)
@Tag(name = "Objection", description = "이의 제기 접수")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dispute-contacts/{token}")
public class ObjectionController {

    private final ObjectionService objectionService;

    @Operation(summary = "이의 제기하기", description = "이메일 검증을 마친 이의 제기 연락처만 접수할 수 있습니다. 접수 즉시 진행 중인 사건이 자동으로 정지됩니다.")
    @PostMapping("/objections")
    public ResponseEntity<RsData<ObjectionResponse>> raise(
            @Parameter(description = "이의 제기 연락처 토큰") @PathVariable String token,
            @Valid @RequestBody ObjectionRequest request
    ) {
        return ResponseEntity.ok(RsData.success(objectionService.raise(token, request)));
    }
}
