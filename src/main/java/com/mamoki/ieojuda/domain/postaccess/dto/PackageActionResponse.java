package com.mamoki.ieojuda.domain.postaccess.dto;

import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.postaccess.entity.PackageActionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// "역할별 사후 패키지" 화면의 박스 하나. 항목(Item) 1개가 박스 1개에 대응한다
public record PackageActionResponse(
        @Schema(description = "항목 ID - 박스를 눌러 단계 상세로 이동할 때 사용", example = "12") Long itemId,
        @Schema(description = "박스 제목(대상 채널·계정명)", example = "메신저") String title,
        @Schema(description = "박스 부제(그 채널에 대해 할 일)", example = "카톡 단체 톡방 전달") String content,
        @Schema(description = "대상 이름", example = "이지수") String targetName,
        @Schema(description = "위치 유형", example = "카카오톡") String locationType,
        @Schema(description = "수행해야 할 행동 전체 설명") String action,
        @Schema(description = "선행 조건 - 먼저 끝나야 하는 일이 있으면 표시") String precondition,
        @Schema(description = "공식 절차 출처 - 이 항목의 근거가 된 작성자 원문") String sourceExcerpt,
        @Schema(description = "박스 상태", example = "IN_PROGRESS", allowableValues = {"COMPLETED", "IN_PROGRESS", "PENDING"}) String status,
        @Schema(description = "완료 처리한 시각 - 미완료면 null") LocalDateTime completedAt
) {
    public static PackageActionResponse of(Item item, PackageActionStatus status) {
        return new PackageActionResponse(
                item.getItemId(),
                item.getTitle(),
                item.getContent(),
                item.getTargetName(),
                item.getLocationType(),
                item.getAction(),
                item.getPrecondition(),
                item.getSourceExcerpt(),
                status.name(),
                item.getCompletedAt()
        );
    }
}
