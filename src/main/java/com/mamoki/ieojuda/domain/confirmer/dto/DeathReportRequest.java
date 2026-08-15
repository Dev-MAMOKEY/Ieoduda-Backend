package com.mamoki.ieojuda.domain.confirmer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

// "사망 신고 이메일" 화면 - 사망하신 날짜를 아는 경우에만 입력, 모르면 비워서 보낸다
public record DeathReportRequest(
        @Schema(description = "사망하신 날짜 (모르면 null)", example = "2026-11-14") LocalDate deathDate
) {
}
