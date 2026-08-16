package com.mamoki.ieojuda.domain.postaccess.dto;

import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// 명세서 "역할별 사후 패키지" 화면 - 본인에게 배정된 항목만 담는다. 다른 역할의 항목·자격증명은 포함하지 않는다
public record PosthumousPackageResponse(
        @Schema(description = "작성자(계획 소유자) 이름", example = "김나무") String ownerName,
        @Schema(description = "담당자 이름", example = "이지수") String assigneeName,
        @Schema(description = "역할 유형", example = "RELATIONSHIP_MANAGER", allowableValues = {"FAMILY_MANAGER", "WORK_MANAGER", "RELATIONSHIP_MANAGER"}) String roleType,
        @Schema(description = "사후 인계 사건 ID - 박스를 눌러 단계 상세로 이동할 때 사용", example = "3") Long caseId,
        @Schema(description = "발송 단계 ID - 박스를 눌러 단계 상세로 이동할 때 사용", example = "7") Long stageId,
        @Schema(description = "완료한 항목 수", example = "1") int completedCount,
        @Schema(description = "전체 항목 수", example = "3") int totalCount,
        @Schema(description = "실행 순서대로 정렬된 박스 목록") List<PackageActionResponse> actions
) {
    public static PosthumousPackageResponse of(HandoverStage stage, List<PackageActionResponse> actions, int completedCount) {
        Recipient recipient = stage.getRecipient();
        return new PosthumousPackageResponse(
                stage.getPlan().getUser().getName(),
                recipient.getName(),
                recipient.getRoleType().name(),
                stage.getReleaseCase().getCaseId(),
                stage.getStageId(),
                completedCount,
                actions.size(),
                actions
        );
    }
}
