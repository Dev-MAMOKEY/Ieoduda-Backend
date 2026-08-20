package com.mamoki.ieojuda.domain.releasecase.dto;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

// 관리자 화면 - caseId로 조회한 사건의 전체 상세 (작성자 본인용 ReleaseStatusResponse와 달리 소유자 무관하게 전체 필드 노출)
public record ReleaseCaseDetailResponse(
        @Schema(description = "사건 ID") UUID caseId,
        @Schema(description = "사건 상태", example = "WAITING") String status,
        @Schema(description = "동결 여부") boolean frozen,
        @Schema(description = "신고 확인 시각") LocalDateTime confirmedAt,
        @Schema(description = "증빙 승인 시각") LocalDateTime evidenceApprovedAt,
        @Schema(description = "예정 발송일") LocalDateTime waitingEndsAt,
        @Schema(description = "취소된 시각") LocalDateTime canceledAt,
        @Schema(description = "완료된 시각") LocalDateTime completedAt,
        @Schema(description = "사건 소유자(작성자) 유저 ID") UUID ownerUserId,
        @Schema(description = "사건 소유자 이메일") String ownerEmail,
        @Schema(description = "사건 소유자 이름") String ownerName
) {
    public static ReleaseCaseDetailResponse of(ReleaseCase releaseCase) {
        User owner = releaseCase.getPlan().getUser();
        return new ReleaseCaseDetailResponse(
                releaseCase.getCaseId(),
                releaseCase.getStatus().name(),
                releaseCase.getFrozen(),
                releaseCase.getConfirmedAt(),
                releaseCase.getEvidenceApprovedAt(),
                releaseCase.getWaitingEndsAt(),
                releaseCase.getCanceledAt(),
                releaseCase.getCompletedAt(),
                owner.getUserId(),
                owner.getEmail(),
                owner.getName()
        );
    }
}
