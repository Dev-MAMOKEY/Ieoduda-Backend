package com.mamoki.ieojuda.domain.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record PackagePreviewResponse(
        @Schema(description = "역할별 패키지 목록") List<RolePackagePreview> packages
) {
}
