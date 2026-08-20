package com.mamoki.ieojuda.domain.confirmer.controller;

import com.mamoki.ieojuda.domain.confirmer.dto.DeathReportInviteResponse;
import com.mamoki.ieojuda.domain.confirmer.dto.DeathReportRequest;
import com.mamoki.ieojuda.domain.confirmer.dto.DeathReportResponse;
import com.mamoki.ieojuda.domain.confirmer.service.DeathReportService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "사망 신고 이메일" 화면 - 수락을 완료한 지정 확인자가 사망 사실을 신고 (로그인 불필요, 초대 토큰이 곧 인증)
@Tag(name = "DeathReport", description = "지정 확인자 - 사망 신고")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/confirmer-acceptances/{token}")
public class DeathReportController {

    private final DeathReportService deathReportService;

    @Operation(summary = "사망 신고 화면 조회", description = "사망 신고 이메일의 링크로 진입했을 때 확인자 이름, 작성자 이름, 발송된 이메일 주소, 만료 시각, 문의 주소를 조회합니다. 만료·사용된 링크는 차단됩니다.")
    @GetMapping("/death-report")
    public ResponseEntity<RsData<DeathReportInviteResponse>> getInvite(
            @Parameter(description = "사망 신고 토큰") @PathVariable String token
    ) {
        return ResponseEntity.ok(RsData.success(deathReportService.getInvite(token)));
    }

    @Operation(summary = "사망 사실 신고", description = "수락 완료한 확인자만 신고할 수 있습니다. 다른 확인자가 이미 신고했고 날짜가 일치하면 사후 인계 사건이 자동 생성됩니다. Idempotency-Key 헤더를 보내면 같은 키의 재전송은 중복 요청(409)으로 응답합니다.")
    @PostMapping("/death-report")
    public ResponseEntity<RsData<DeathReportResponse>> report(
            @Parameter(description = "초대 토큰") @PathVariable String token,
            @RequestBody(required = false) DeathReportRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return ResponseEntity.ok(RsData.success(deathReportService.report(token, request, idempotencyKey)));
    }
}
