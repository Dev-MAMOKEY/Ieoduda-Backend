package com.mamoki.ieojuda.domain.handoffcheck.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// "선택형 생전 인계 점검" 화면 전체 - 담당자 박스 목록 + 확인자 박스 목록 + 헤더 카운트
public record HandoffCheckStatusResponse(
        @Schema(description = "역할 담당자 총 인원 (대체 담당자 제외)") long assigneeTotalCount,
        @Schema(description = "역할 담당자 중 준비 완료 인원") long assigneeReadyCount,
        @Schema(description = "역할 담당자 박스 목록") List<HandoffCheckAssigneeResponse> assignees,
        @Schema(description = "지정 확인자 총 인원") long confirmerTotalCount,
        @Schema(description = "지정 확인자 중 준비 완료 인원") long confirmerReadyCount,
        @Schema(description = "지정 확인자 박스 목록") List<HandoffCheckConfirmerResponse> confirmers
) {
    public static HandoffCheckStatusResponse of(List<HandoffCheckAssigneeResponse> assignees,
                                                 List<HandoffCheckConfirmerResponse> confirmers) {
        long assigneeReadyCount = assignees.stream().filter(HandoffCheckAssigneeResponse::isReady).count();
        long confirmerReadyCount = confirmers.stream().filter(HandoffCheckConfirmerResponse::isReady).count();

        return new HandoffCheckStatusResponse(
                assignees.size(),
                assigneeReadyCount,
                assignees,
                confirmers.size(),
                confirmerReadyCount,
                confirmers
        );
    }
}
