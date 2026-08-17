package com.mamoki.ieojuda.domain.postaccess.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// 명세서 "역할별 사후 패키지" - 인증된 담당자가 자신의 역할에 필요한 대상·행동·순서만 확인하는 화면.
// 다른 역할의 항목과 자격증명(원문)은 어떤 필드에도 포함하지 않는다.
public record PosthumousPackageResponse(
        @Schema(description = "역할 한글 명칭", example = "관계 정리 담당자") String roleLabel,
        @Schema(description = "작성자(계획 소유자) 이름") String authorName,
        @Schema(description = "전체 행동 수") int totalCount,
        @Schema(description = "완료된 행동 수") int completedCount,
        @Schema(description = "안내 문구") String notice,
        @Schema(description = "행동 목록") List<PackageActionResponse> actions
) {
}
