package com.mamoki.ieojuda.domain.postaccess.controller;

import com.mamoki.ieojuda.domain.postaccess.dto.OtpSendResponse;
import com.mamoki.ieojuda.domain.postaccess.dto.OtpVerifyRequest;
import com.mamoki.ieojuda.domain.postaccess.dto.OtpVerifyResponse;
import com.mamoki.ieojuda.domain.postaccess.dto.PosthumousAccessResponse;
import com.mamoki.ieojuda.domain.postaccess.service.PosthumousAccessService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "사후 인계 이메일" 화면 - 역할 담당자가 이메일의 보안 링크로 접근하는 엔드포인트라 로그인이 필요 없다(토큰이 곧 인증)
@Tag(name = "PosthumousAccess", description = "사후 인계 이메일 - 링크 검증 / OTP 발송 / OTP 확인")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posthumous-access")
public class PosthumousAccessController {

    private final PosthumousAccessService posthumousAccessService;

    @Operation(summary = "링크 검증", description = "사후 인계 이메일의 보안 링크로 진입했을 때 담당자·작성자·역할명을 조회합니다. 인증 전이라 인계 내용은 포함하지 않습니다.")
    @GetMapping("/{token}")
    public ResponseEntity<RsData<PosthumousAccessResponse>> verifyLink(
            @Parameter(description = "사후 인계 링크 토큰") @PathVariable String token
    ) {
        return ResponseEntity.ok(RsData.success(posthumousAccessService.verifyLink(token)));
    }

    @Operation(summary = "인증번호 발송", description = "화면의 '인증번호 발송하기' / '코드 재발송' - 4자리 OTP를 담당자 이메일로 발송합니다. 재발송 시 시도 횟수가 초기화됩니다.")
    @PostMapping("/{token}/otp")
    public ResponseEntity<RsData<OtpSendResponse>> sendOtp(
            @Parameter(description = "사후 인계 링크 토큰") @PathVariable String token
    ) {
        return ResponseEntity.ok(RsData.success(posthumousAccessService.sendOtp(token)));
    }

    @Operation(summary = "인증 코드 확인", description = "화면의 '인증 코드 입력' - OTP를 확인합니다. 5회 실패 시 차단됩니다. 성공 시 다음 화면 분기 정보를 반환합니다.")
    @PostMapping("/{token}/verify")
    public ResponseEntity<RsData<OtpVerifyResponse>> verifyOtp(
            @Parameter(description = "사후 인계 링크 토큰") @PathVariable String token,
            @Valid @RequestBody OtpVerifyRequest request
    ) {
        return ResponseEntity.ok(RsData.success(posthumousAccessService.verifyOtp(token, request)));
    }
}
