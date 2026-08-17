package com.mamoki.ieojuda.domain.handoffcheck.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

// "선택형 생전 인계 점검" 화면 - 점검 발송 대상 지정 (생략 시 대체 담당자를 제외한 전체 담당자)
public record HandoffCheckSendRequest(
        @Schema(description = "점검 발송 대상 담당자 ID 목록 - 생략 시 전체 담당자에게 발송") List<UUID> recipientIds
) {
}
