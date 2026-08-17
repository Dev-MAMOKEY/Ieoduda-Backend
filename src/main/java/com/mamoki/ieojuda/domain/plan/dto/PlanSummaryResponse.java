package com.mamoki.ieojuda.domain.plan.dto;

import com.mamoki.ieojuda.domain.releasecase.dto.ReleaseStatusResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

// "계획 홈" 화면 전체 - 프론트가 이 화면 하나를 그리는 데 필요한 정보를 한 번의 호출로 제공한다.
// 종합 AI 준비도 점수는 명세서에서 "제공하지 않습니다"라고 명시했으므로 포함하지 않는다.
public record PlanSummaryResponse(
        @Schema(description = "계획 ID") UUID planId,
        @Schema(description = "계획 상태", example = "DRAFT", allowableValues = {"DRAFT", "SEALED", "DEACTIVATED"}) String status,
        @Schema(description = "작성자 이름") String userName,
        @Schema(description = "구역별 요약 (가족/관계 정리/업무 연속성)") List<LifeAreaSummaryResponse> lifeAreas,
        @Schema(description = "역할 담당자 중 수락 완료 인원") long recipientAcceptedCount,
        @Schema(description = "역할 담당자 총 인원") long recipientTotalCount,
        @Schema(description = "지정 확인자 중 수락 완료 인원") long confirmerAcceptedCount,
        @Schema(description = "지정 확인자 총 인원") long confirmerTotalCount,
        @Schema(description = "지정 확인자 이름 목록") List<String> confirmerNames,
        @Schema(description = "대체 담당자가 등록되지 않은 역할 담당자 수") long backupMissingCount,
        @Schema(description = "해결되지 않은 실행 순서 충돌에 걸린 항목 수") long unresolvedConflictCount,
        @Schema(description = "선택한 대기 기간(일) - 아직 설정 안 했으면 null") Integer waitingDays,
        @Schema(description = "진행 중인 사후 인계 사건 상태") ReleaseStatusResponse releaseCase
) {
}
