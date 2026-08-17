package com.mamoki.ieojuda.domain.partner.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// issue #43 - 짧은 수명의 1회성 다운로드 토큰 발급 결과. 이 토큰으로 한 번만 원본을 내려받을 수 있다.
public record EvidenceDownloadLinkResponse(
        @Schema(description = "1회성 다운로드 토큰") String downloadToken,
        @Schema(description = "토큰 만료 시각") LocalDateTime expiresAt
) {
}
