package com.mamoki.ieojuda.domain.audit.controller;

import java.util.UUID;

import com.mamoki.ieojuda.domain.account.entity.AdminPermission;
import com.mamoki.ieojuda.domain.audit.dto.AdminActionAuditLogResponse;
import com.mamoki.ieojuda.domain.audit.service.AdminActionAuditService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import com.mamoki.ieojuda.global.security.PermissionGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// issue #59 "고위험 조작 감사" 화면 - 사건 동결·파트너 배정·증빙 판정의 호출자와 결과를 조회
@Tag(name = "Admin - Action Audit", description = "운영관리자 - 사건 동결/파트너 배정/증빙 판정 감사")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/action-audit-logs")
public class AdminActionAuditController {

    private final AdminActionAuditService adminActionAuditService;
    private final PermissionGuard permissionGuard;

    @Operation(summary = "관리자 조작 감사 조회", description = "고위험 조작(사건 동결/파트너 배정/증빙 판정)의 호출자·성공 여부를 최신순으로 페이지 단위 조회합니다.")
    @GetMapping
    public ResponseEntity<RsData<Page<AdminActionAuditLogResponse>>> getLogs(
            @AuthenticationPrincipal UUID userId,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        permissionGuard.require(userId, AdminPermission.AUDIT_VIEW);
        return ResponseEntity.ok(RsData.success(adminActionAuditService.getLogs(pageable)));
    }
}
