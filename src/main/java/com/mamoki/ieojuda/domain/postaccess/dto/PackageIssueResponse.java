package com.mamoki.ieojuda.domain.postaccess.dto;

import java.util.UUID;

import com.mamoki.ieojuda.domain.postaccess.entity.PackageIssue;
import io.swagger.v3.oas.annotations.media.Schema;

public record PackageIssueResponse(
        @Schema(description = "신고 ID") UUID issueId,
        @Schema(description = "문제가 발생한 행동 ID") UUID actionId,
        @Schema(description = "처리 상태", allowableValues = {"OPEN", "RESOLVED"}) String status
) {
    public static PackageIssueResponse from(PackageIssue issue) {
        return new PackageIssueResponse(
                issue.getIssueId(),
                issue.getItem().getItemId(),
                issue.getStatus().name()
        );
    }
}
