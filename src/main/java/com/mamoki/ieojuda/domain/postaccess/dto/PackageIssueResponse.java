package com.mamoki.ieojuda.domain.postaccess.dto;

import com.mamoki.ieojuda.domain.postaccess.entity.PackageIssue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// "역할별 사후 패키지" 화면 - "문제 신고하기" 접수 결과
public record PackageIssueResponse(
        @Schema(description = "신고 ID", example = "4") Long issueId,
        @Schema(description = "문제가 발생한 항목 ID", example = "12") Long itemId,
        @Schema(description = "신고 처리 상태", example = "OPEN", allowableValues = {"OPEN", "RESOLVED"}) String status,
        @Schema(description = "신고 접수 시각") LocalDateTime reportedAt
) {
    public static PackageIssueResponse from(PackageIssue issue) {
        return new PackageIssueResponse(
                issue.getIssueId(),
                issue.getItem().getItemId(),
                issue.getStatus().name(),
                issue.getReportedAt()
        );
    }
}
