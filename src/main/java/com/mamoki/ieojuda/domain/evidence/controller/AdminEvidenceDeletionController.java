package com.mamoki.ieojuda.domain.evidence.controller;

import java.util.UUID;

import com.mamoki.ieojuda.domain.evidence.dto.EvidenceDeletionStatusResponse;
import com.mamoki.ieojuda.domain.evidence.service.EvidenceDeletionService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 관리자 "증빙 삭제 감사" 목록 화면 - evidenceId를 몰라도 전체 증빙을 조회할 수 있어야 함
@Tag(name = "Admin - Evidence Deletion Audit", description = "운영관리자 - 증빙 삭제 감사 목록 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/evidence")
public class AdminEvidenceDeletionController {

    private final EvidenceDeletionService evidenceDeletionService;

    @Operation(summary = "증빙 삭제 감사 목록 조회", description = "전체 증빙의 검토완료일·삭제예정일·삭제상태를 페이지 단위로 조회합니다.")
    @GetMapping("/deletion-statuses")
    public ResponseEntity<RsData<Page<EvidenceDeletionStatusResponse>>> getStatuses(
            @AuthenticationPrincipal UUID userId,
            @PageableDefault(size = 50, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(RsData.success(evidenceDeletionService.getAllStatuses(userId, pageable)));
    }
}
