package com.mamoki.ieojuda.domain.evidence.controller;

import com.mamoki.ieojuda.domain.evidence.dto.EvidenceDeletionStatusResponse;
import com.mamoki.ieojuda.domain.evidence.service.EvidenceDeletionService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "증빙 삭제 감사" 화면 - 서비스 운영자·외부 파트너 공용
// issue #59 - EVIDENCE_DELETE_RETRY 세부 권한 검사
@Tag(name = "Evidence Deletion Audit", description = "증빙 삭제 감사 조회 / 재처리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/evidence/{evidenceId}")
public class EvidenceDeletionController {

    private final EvidenceDeletionService evidenceDeletionService;

    @Operation(summary = "증빙 삭제 상태 조회", description = "검토완료일·삭제예정일·삭제상태·무결성해시·실패사유를 조회합니다.")
    @GetMapping("/deletion-status")
    public ResponseEntity<RsData<EvidenceDeletionStatusResponse>> getStatus(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "증빙 ID") @PathVariable Long evidenceId
    ) {
        return ResponseEntity.ok(RsData.success(evidenceDeletionService.getStatus(userId, evidenceId)));
    }

    @Operation(summary = "삭제 재처리 요청", description = "자동 삭제가 실패한 증빙을 다시 삭제 시도합니다.")
    @PostMapping("/deletion-retry")
    public ResponseEntity<RsData<EvidenceDeletionStatusResponse>> retryDeletion(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "증빙 ID") @PathVariable Long evidenceId
    ) {
        return ResponseEntity.ok(RsData.success(evidenceDeletionService.retryDeletion(userId, evidenceId)));
    }
}
