package com.mamoki.ieojuda.domain.account.controller;

import java.util.UUID;

import com.mamoki.ieojuda.domain.account.service.AdminUserService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// issue #56 - 운영관리자 전용, 계정 정지/복구. 정지 시 그 계정의 모든 세션(Access/Refresh Token)을
// 즉시 폐기한다.
@Tag(name = "Admin - User", description = "운영관리자 - 계정 정지 / 복구")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users/{userId}")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "계정 정지", description = "해당 계정의 모든 세션을 즉시 폐기하고 정지 상태로 전환합니다.")
    @PostMapping("/suspend")
    public ResponseEntity<RsData<Void>> suspend(
            @Parameter(description = "사용자 ID") @PathVariable UUID userId
    ) {
        adminUserService.suspend(userId);
        return ResponseEntity.ok(RsData.success(null));
    }

    @Operation(summary = "계정 정지 해제")
    @PostMapping("/reactivate")
    public ResponseEntity<RsData<Void>> reactivate(
            @Parameter(description = "사용자 ID") @PathVariable UUID userId
    ) {
        adminUserService.reactivate(userId);
        return ResponseEntity.ok(RsData.success(null));
    }
}
