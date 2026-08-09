package com.mamoki.ieojuda.domain.account.controller;

import com.mamoki.ieojuda.domain.account.dto.LoginRequest;
import com.mamoki.ieojuda.domain.account.dto.RefreshRequest;
import com.mamoki.ieojuda.domain.account.dto.SignupRequest;
import com.mamoki.ieojuda.domain.account.dto.SignupResponse;
import com.mamoki.ieojuda.domain.account.dto.TokenResponse;
import com.mamoki.ieojuda.domain.account.service.AuthService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "회원가입 / 로그인" 화면
@Tag(name = "Auth", description = "회원가입 / 로그인 / 토큰 재발급")
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입", description = "이메일/비밀번호/비밀번호 확인/이름으로 계정을 생성합니다.")
    @PostMapping("/signup")
    public ResponseEntity<RsData<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.ok(RsData.success(authService.signup(request)));
    }

    @Operation(summary = "로그인", description = "이메일/비밀번호로 로그인하고 Access/Refresh Token을 발급합니다.")
    @PostMapping("/login")
    public ResponseEntity<RsData<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(RsData.success(authService.login(request)));
    }

    @Operation(summary = "토큰 재발급", description = "Refresh Token으로 Access/Refresh Token을 새로 발급합니다 (재발급마다 Refresh Token도 회전).")
    @PostMapping("/refresh")
    public ResponseEntity<RsData<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(RsData.success(authService.refresh(request)));
    }
}
