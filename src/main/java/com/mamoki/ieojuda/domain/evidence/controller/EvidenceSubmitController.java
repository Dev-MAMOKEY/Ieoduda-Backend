package com.mamoki.ieojuda.domain.evidence.controller;

import com.mamoki.ieojuda.domain.evidence.dto.EvidenceSubmitResponse;
import com.mamoki.ieojuda.domain.evidence.service.EvidenceSubmitService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// 명세서 "공식 증빙 자료 제출" 화면 - 지정 확인자가 사망진단서 등 증빙 파일을 제출 (로그인 불필요, 초대 토큰이 곧 인증)
@Tag(name = "EvidenceSubmit", description = "공식 증빙 자료 제출")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/confirmer-acceptances/{token}")
public class EvidenceSubmitController {

    private final EvidenceSubmitService evidenceSubmitService;

    @Operation(summary = "증빙 자료 제출", description = "PDF/JPG/PNG, 최대 10MB, 사건당 최대 3개까지 제출할 수 있습니다.")
    @PostMapping(value = "/evidences", consumes = "multipart/form-data")
    public ResponseEntity<RsData<EvidenceSubmitResponse>> submit(
            @Parameter(description = "초대 토큰") @PathVariable String token,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(RsData.success(evidenceSubmitService.submit(token, file)));
    }
}
