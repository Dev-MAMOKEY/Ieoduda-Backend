package com.mamoki.ieojuda.domain.releasecase.dto;

import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.time.LocalDateTime;

// "사후 인계" 화면 - 경고 메일 링크(비로그인)로 접근한 작성자 본인 / 이의 제기 연락처가 보는 대기 상태.
// 로그인 세션이 있는 ReleaseStatusResponse와 달리, 토큰의 purpose로 어떤 동작을 보여줄지 결정한다.
public record PublicWaitingStatusResponse(
        @Schema(description = "진행 중인 사후 인계 사건이 있는지 여부") boolean hasActiveCase,
        @Schema(description = "사건 상태", example = "WAITING") String status,
        @Schema(description = "예정 발송일 (대기 중일 때만 값 존재)") LocalDateTime waitingEndsAt,
        @Schema(description = "남은 기간(일) - 대기 중일 때만 값 존재") Long remainingDays,
        @Schema(description = "취소된 시각 (취소 안 됐으면 null)") LocalDateTime canceledAt,
        @Schema(description = "이 토큰으로 수행 가능한 동작", example = "CANCEL") String availableAction
) {
    public static PublicWaitingStatusResponse of(ReleaseCase releaseCase, SecurityTokenPurpose purpose) {
        Long remainingDays = releaseCase.getWaitingEndsAt() == null
                ? null
                : Math.max(0, Duration.between(LocalDateTime.now(), releaseCase.getWaitingEndsAt()).toDays());
        String availableAction = purpose == SecurityTokenPurpose.CANCEL_CASE ? "CANCEL" : "RAISE_OBJECTION";
        return new PublicWaitingStatusResponse(
                true,
                releaseCase.getStatus().name(),
                releaseCase.getWaitingEndsAt(),
                remainingDays,
                releaseCase.getCanceledAt(),
                availableAction
        );
    }
}
