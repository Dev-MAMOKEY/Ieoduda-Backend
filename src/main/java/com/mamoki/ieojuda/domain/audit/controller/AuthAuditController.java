package com.mamoki.ieojuda.domain.audit.controller;

import java.util.UUID;

import com.mamoki.ieojuda.domain.account.entity.AdminPermission;
import com.mamoki.ieojuda.domain.audit.dto.AuthAuditLogResponse;
import com.mamoki.ieojuda.domain.audit.service.AuthAuditService;
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

// issue #55 "인증 실패 감사" 화면 - 운영관리자 전용. 로그인 실패/잠금, rate limit 초과 이력을 최신순으로 조회
// issue #59 - ROLE_ADMIN이어도 AUDIT_VIEW 세부 권한이 있어야 조회 가능하도록 세분화
@Tag(name = "Admin - Auth Audit", description = "운영관리자 - 로그인 실패 / 잠금 / rate limit 초과 감사")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/auth-audit-logs")
public class AuthAuditController {

    private final AuthAuditService authAuditService;
    private final PermissionGuard permissionGuard;

    @Operation(summary = "인증 실패 감사 조회", description = "로그인 실패/잠금, rate limit 초과 이력을 최신순으로 페이지 단위 조회합니다.")
    @GetMapping
    public ResponseEntity<RsData<Page<AuthAuditLogResponse>>> getLogs(
            @AuthenticationPrincipal UUID userId,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        permissionGuard.require(userId, AdminPermission.AUDIT_VIEW);
        return ResponseEntity.ok(RsData.success(authAuditService.getLogs(pageable)));
    }
}
