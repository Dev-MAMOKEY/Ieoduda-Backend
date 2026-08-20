package com.mamoki.ieojuda.domain.evidence.controller;

import java.util.UUID;

import com.mamoki.ieojuda.domain.evidence.dto.EvidenceSubmitResponse;
import com.mamoki.ieojuda.domain.evidence.entity.EvidenceType;
import com.mamoki.ieojuda.domain.evidence.service.EvidenceSubmitService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// 명세서 "공식 증빙 자료 제출" 화면 - 지정 확인자가 사망진단서 등 증빙 파일을 제출 (로그인 불필요, 초대 토큰이 곧 인증)
@Tag(name = "EvidenceSubmit", description = "공식 증빙 자료 제출")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/release-cases/{caseId}/evidence")
public class EvidenceSubmitController {

    private final EvidenceSubmitService evidenceSubmitService;

    @Operation(summary = "증빙 자료 제출", description = "PDF/JPG/PNG, 파일당 최대 50MB, 요청 전체 최대 55MB, 사건당 최대 3개까지 제출할 수 있습니다. 증빙 종류(evidenceType)는 필수이며 선택하지 않으면 거부됩니다. Idempotency-Key 헤더를 보내면 같은 키의 재전송은 중복 요청(409)으로 응답합니다.")
    @PostMapping(value = "/submit", consumes = "multipart/form-data")
    public ResponseEntity<RsData<EvidenceSubmitResponse>> submit(
            @Parameter(description = "사건 ID") @PathVariable UUID caseId,
            @Parameter(description = "초대 토큰") @RequestParam("token") String token,
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "증빙 종류") @RequestParam("evidenceType") EvidenceType evidenceType,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return ResponseEntity.ok(RsData.success(evidenceSubmitService.submit(caseId, token, file, evidenceType, idempotencyKey)));
    }
}
