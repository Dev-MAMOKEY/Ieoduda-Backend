package com.mamoki.ieojuda.domain.account.controller;

import java.util.UUID;

import com.mamoki.ieojuda.domain.account.dto.ConsentResponse;
import com.mamoki.ieojuda.domain.account.dto.UserResponse;
import com.mamoki.ieojuda.domain.account.dto.UserUpdateRequest;
import com.mamoki.ieojuda.domain.account.service.UserService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "마이페이지" 화면 - 계정 정보 변경 / 삭제
@Tag(name = "User", description = "마이페이지 - 계정 정보 변경 / 삭제")
@RestController
@RequiredArgsConstructor
@RequestMapping("/users/me")
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 조회")
    @GetMapping
    public ResponseEntity<RsData<UserResponse>> getMe(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(RsData.success(userService.getMe(userId)));
    }

    @Operation(summary = "이메일/이름 변경")
    @PutMapping
    public ResponseEntity<RsData<UserResponse>> update(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        return ResponseEntity.ok(RsData.success(userService.updateProfile(userId, request)));
    }

    @Operation(summary = "필수 동의 상태 조회", description = "\"사후 인계 안내\" 화면의 필수 동의를 완료했는지 조회합니다.")
    @GetMapping("/consent")
    public ResponseEntity<RsData<ConsentResponse>> getConsent(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(RsData.success(userService.getConsentStatus(userId)));
    }

    // issue #94 - 동의 "제출"은 명세서 "서비스 안내 / 안전 동의" 페이지가 요구하는 대로
    // POST /api/consents로 옮겼다 (ConsentController). 상태 "조회"는 명세서에 없는 추가 API라
    // 여기 남겨둔다.

    @Operation(summary = "계정 삭제", description = "계정과 계획(대화·항목·담당자 등) 데이터를 영구 삭제합니다. 되돌릴 수 없습니다.")
    @DeleteMapping
    public ResponseEntity<RsData<Void>> delete(@AuthenticationPrincipal UUID userId) {
        userService.deleteAccount(userId);
        return ResponseEntity.ok(RsData.success(null));
    }
}
