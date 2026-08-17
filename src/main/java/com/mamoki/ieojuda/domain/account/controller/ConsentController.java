package com.mamoki.ieojuda.domain.account.controller;

import java.util.UUID;

import com.mamoki.ieojuda.domain.account.dto.ConsentResponse;
import com.mamoki.ieojuda.domain.account.service.UserService;
import com.mamoki.ieojuda.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 명세서 "서비스 안내 / 안전 동의" 화면 - 필수 동의 기록
// issue #94 - 명세서 경로(POST /api/consents)와 실제 구현(POST /users/me/consent)이 어긋나 있던 것을
// 명세서 경로로 정렬했다. 상태 조회(GET)는 명세서에 없는 API라 UserController(/users/me/consent)에 남아있다.
@Tag(name = "Consent", description = "필수 동의 기록")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/consents")
public class ConsentController {

    private final UserService userService;

    @Operation(summary = "필수 동의", description = "\"사후 인계 안내\" 화면의 필수 동의를 완료합니다. 동의 전에는 다른 API를 호출할 수 없습니다.")
    @PostMapping
    public ResponseEntity<RsData<ConsentResponse>> agree(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(RsData.success(userService.agree(userId)));
    }
}
