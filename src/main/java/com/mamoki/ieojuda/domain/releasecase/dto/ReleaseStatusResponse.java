package com.mamoki.ieojuda.domain.releasecase.dto;

import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.time.LocalDateTime;

// "사후 인계" 화면 - 작성자 본인이 보는 대기 상태
public record ReleaseStatusResponse(
        @Schema(description = "진행 중인 사후 인계 사건이 있는지 여부 - 없으면 평상시(대기) 상태") boolean hasActiveCase,
        @Schema(description = "사건 상태", example = "WAITING") String status,
        @Schema(description = "예정 발송일 (대기 중일 때만 값 존재)") LocalDateTime waitingEndsAt,
        @Schema(description = "남은 기간(일) - 대기 중일 때만 값 존재") Long remainingDays,
        @Schema(description = "취소된 시각 (취소 안 됐으면 null)") LocalDateTime canceledAt
) {
    public static ReleaseStatusResponse of(ReleaseCase releaseCase) {
        if (releaseCase == null) {
            return new ReleaseStatusResponse(false, null, null, null, null);
        }
        Long remainingDays = releaseCase.getWaitingEndsAt() == null
                ? null
                : Math.max(0, Duration.between(LocalDateTime.now(), releaseCase.getWaitingEndsAt()).toDays());
        return new ReleaseStatusResponse(
                true,
                releaseCase.getStatus().name(),
                releaseCase.getWaitingEndsAt(),
                remainingDays,
                releaseCase.getCanceledAt()
        );
    }
}
