package com.mamoki.ieojuda.domain.handoffcheck.dto;

import java.util.UUID;

import com.mamoki.ieojuda.domain.handoffcheck.entity.HandoffCheckResponse;
import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// "선택형 생전 인계 점검" 화면 - 역할 담당자 한 명당 박스 하나
//
// 필드가 두 그룹으로 나뉜다 - 서로 다른 시점·출처의 데이터이므로 섞어 쓰지 않는다.
//   1) isEmailSent / isRoleAccepted / inquiry
//      → "역할 담당자 등록" 시 보낸 역할 수락 초대 이메일(Recipient)의 발송·수락·문의 상태
//   2) lastCheckSentAt / check* 6개 (checkEmailReached ~ checkRespondedAt)
//      → 이 화면 자체의 "인계 점검" 메일(HandoffCheck/HandoffCheckResponse)에 대해 담당자가 실제로 답한 내용
// 명세서 「선택형 생전 인계 점검」의 "이메일 도달·역할 이해·공개 범주 이해·문의 사항"은 2)번 check* 필드가 정확히 대응한다.
public record HandoffCheckAssigneeResponse(
        @Schema(description = "담당자 ID") UUID assigneeId,
        @Schema(description = "담당자 이름") String name,
        @Schema(description = "역할 유형", example = "RELATIONSHIP_MANAGER", allowableValues = {"FAMILY_MANAGER", "WORK_MANAGER", "RELATIONSHIP_MANAGER"}) String roleType,
        @Schema(description = "[역할 수락 초대] 수락 요청 이메일 발송 완료 여부 - 인계 점검 메일과 무관, checkEmailReached와 혼동 금지") boolean isEmailSent,
        @Schema(description = "[역할 수락 초대] 역할 수락 완료 여부 - 인계 점검 메일과 무관, checkRoleUnderstood와 혼동 금지") boolean isRoleAccepted,
        @Schema(description = "대체 담당자 이름 - 등록되지 않았으면 null") String backupName,
        @Schema(description = "대체 담당자의 역할 수락 완료 여부 - 대체 담당자가 없으면 false") boolean isBackupAccepted,
        @Schema(description = "[역할 수락 초대] 담당자가 수락/거절 시 남긴 문의 사항 - 없으면 null. 인계 점검 응답 문의는 checkInquiry 참고") String inquiry,
        @Schema(description = "준비 완료 여부 - 이메일 발송, 역할 수락이 끝나야 하고, 대체 담당자가 있다면 그 수락까지 끝나야 true") boolean isReady,
        @Schema(description = "[인계 점검] 가장 최근 인계 점검 메일 발송 시각 - 발송한 적 없으면 null") LocalDateTime lastCheckSentAt,
        @Schema(description = "[인계 점검] 담당자가 답한 이메일 도달 여부 - 명세서 '이메일 도달'에 대응. 아직 응답 전이면 null") Boolean checkEmailReached,
        @Schema(description = "[인계 점검] 담당자가 답한 역할 이해 여부 - isRoleAccepted(역할 수락 여부)와 다른 개념. 아직 응답 전이면 null") Boolean checkRoleUnderstood,
        @Schema(description = "[인계 점검] 담당자가 답한 공개 범주 이해 여부 - 명세서 '공개 범주 이해'에 대응. 아직 응답 전이면 null") Boolean checkScopeUnderstood,
        @Schema(description = "[인계 점검] 응답 시 남긴 문의 사항 - 명세서 '문의 사항'에 대응. inquiry(역할 수락 문의)와는 별개. 없으면 null") String checkInquiry,
        @Schema(description = "[인계 점검] 응답 시각 - 아직 응답 전이면 null") LocalDateTime checkRespondedAt
) {
    public static HandoffCheckAssigneeResponse of(Recipient recipient, Recipient backup, HandoffCheckResponse latestCheck) {
        boolean isEmailSent = Boolean.TRUE.equals(recipient.getInviteSent());
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
                isReady,
                latestCheck == null ? null : latestCheck.getHandoffCheck().getSentAt(),
                latestCheck == null ? null : latestCheck.getEmailReached(),
                latestCheck == null ? null : latestCheck.getRoleUnderstood(),
                latestCheck == null ? null : latestCheck.getDisclosureUnderstood(),
                latestCheck == null ? null : latestCheck.getInquiry(),
                latestCheck == null ? null : latestCheck.getRespondedAt()
        );
    }
}
