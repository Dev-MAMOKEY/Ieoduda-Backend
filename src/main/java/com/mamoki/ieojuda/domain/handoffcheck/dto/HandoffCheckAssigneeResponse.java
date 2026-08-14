package com.mamoki.ieojuda.domain.handoffcheck.dto;

import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import io.swagger.v3.oas.annotations.media.Schema;

// "선택형 생전 인계 점검" 화면 - 역할 담당자 한 명당 박스 하나
public record HandoffCheckAssigneeResponse(
        @Schema(description = "담당자 ID") Long assigneeId,
        @Schema(description = "담당자 이름") String name,
        @Schema(description = "역할 유형", example = "RELATIONSHIP_MANAGER", allowableValues = {"FAMILY_MANAGER", "WORK_MANAGER", "RELATIONSHIP_MANAGER"}) String roleType,
        @Schema(description = "수락 요청 이메일 발송 완료 여부") boolean isEmailSent,
        @Schema(description = "역할 수락 완료 여부") boolean isRoleAccepted,
        @Schema(description = "대체 담당자 이름 - 등록되지 않았으면 null") String backupName,
        @Schema(description = "대체 담당자의 역할 수락 완료 여부 - 대체 담당자가 없으면 false") boolean isBackupAccepted,
        @Schema(description = "담당자가 수락/거절 시 남긴 문의 사항 - 없으면 null") String inquiry,
        @Schema(description = "준비 완료 여부 - 이메일 발송, 역할 수락이 끝나야 하고, 대체 담당자가 있다면 그 수락까지 끝나야 true") boolean isReady
) {
    public static HandoffCheckAssigneeResponse of(Recipient recipient, Recipient backup) {
        // 초대 토큰이 발급됐다는 것은 수락 요청 이메일이 나갔다는 뜻 (별도 발송 이력 컬럼이 없음)
        boolean isEmailSent = recipient.getInviteToken() != null;
        boolean isRoleAccepted = recipient.getAcceptanceStatus() == AcceptanceStatus.ACCEPTED;
        boolean isBackupAccepted = backup != null && backup.getAcceptanceStatus() == AcceptanceStatus.ACCEPTED;
        // 대체 담당자는 필수가 아니므로 없으면 판정에서 제외하고, 있으면 그 수락 여부까지 조건에 포함한다
        boolean isReady = isEmailSent && isRoleAccepted && (backup == null || isBackupAccepted);

        return new HandoffCheckAssigneeResponse(
                recipient.getAssigneeId(),
                recipient.getName(),
                recipient.getRoleType().name(),
                isEmailSent,
                isRoleAccepted,
                backup == null ? null : backup.getName(),
                isBackupAccepted,
                recipient.getInquiry(),
                isReady
        );
    }
}
