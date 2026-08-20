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

// 명세서 "사후 인계 이메일" - 역할 담당자가 만료 링크와 별도 이메일 OTP 확인 후 패키지에 접근하기 전 단계 (로그인 불필요, 토큰이 곧 인증)
@Tag(name = "PosthumousAccess", description = "사후 인계 - 링크 검증 / OTP 발송 / OTP 확인 (이메일 링크 클릭)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posthumous-access")
public class PosthumousAccessController {

    private final PosthumousAccessService posthumousAccessService;

    @Operation(summary = "링크 검증", description = "사후 인계 이메일의 링크로 진입했을 때 담당자·작성자 이름, 역할, 링크 만료 시각, OTP 발송 여부를 조회합니다. 인증 전이므로 인계 내용은 포함하지 않습니다.")
    @GetMapping("/{token}")
    public ResponseEntity<RsData<PosthumousAccessResponse>> getAccess(
            @Parameter(description = "사후 인계 링크 토큰") @PathVariable String token
    ) {
        return ResponseEntity.ok(RsData.success(posthumousAccessService.getAccess(token)));
    }

    @Operation(summary = "OTP 발송", description = "담당자의 등록된 이메일로 별도의 4자리 OTP 코드를 발송합니다. 재발송 간격과 최대 시도 횟수 제한이 적용됩니다.")
    @PostMapping("/{token}/otp")
    public ResponseEntity<RsData<OtpSendResponse>> sendOtp(
            @Parameter(description = "사후 인계 링크 토큰") @PathVariable String token
    ) {
        return ResponseEntity.ok(RsData.success(posthumousAccessService.sendOtp(token)));
    }

    @Operation(summary = "OTP 확인", description = "발송된 OTP 코드를 검증합니다. 성공 시 역할별 사후 패키지 조회에 사용할 열람 세션을 발급합니다.")
    @PostMapping("/{token}/verify")
    public ResponseEntity<RsData<OtpVerifyResponse>> verify(
            @Parameter(description = "사후 인계 링크 토큰") @PathVariable String token,
            @Valid @RequestBody OtpVerifyRequest request
    ) {
        return ResponseEntity.ok(RsData.success(posthumousAccessService.verify(token, request)));
    }
}
