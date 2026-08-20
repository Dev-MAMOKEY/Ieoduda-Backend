package com.mamoki.ieojuda.domain.plan.dto;

import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;
import java.util.List;

// 명세서 "역할별 패키지 미리보기" - 담당자 한 명에게 공개될 정보와 행동
public record RolePackagePreview(
        @Schema(description = "담당자 ID") UUID recipientId,
        @Schema(description = "담당자 이름") String recipientName,
        @Schema(description = "역할 종류") String roleType,
        @Schema(description = "공개 범위") String disclosureScope,
        @Schema(description = "이 담당자에게 공개될 행동 목록") List<PackageActionPreview> actions,
        @Schema(description = "이 패키지에서 제외되는 것") List<String> excluded
) {
    private static final List<String> EXCLUDED = List.of("다른 역할의 항목", "자격증명");

    public static RolePackagePreview of(Recipient recipient, List<PackageActionPreview> actions) {
        return new RolePackagePreview(
                recipient.getAssigneeId(),
                recipient.getName(),
                recipient.getRoleType().name(),
                recipient.getDisclosureScope() == null ? null : recipient.getDisclosureScope().name(),
                actions,
                EXCLUDED
        );
    }
}
