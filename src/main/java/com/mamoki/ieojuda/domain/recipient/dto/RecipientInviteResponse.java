package com.mamoki.ieojuda.domain.recipient.dto;

import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

// "역할 수락 이메일" 화면 - 초대 링크로 진입했을 때 보여줄 내용
public record RecipientInviteResponse(
        @Schema(description = "담당자 이름", example = "이지수") String assigneeName,
        @Schema(description = "작성자(계획 소유자) 이름", example = "김나무") String ownerName,
        @Schema(description = "역할 유형", example = "RELATIONSHIP_MANAGER", allowableValues = {"FAMILY_MANAGER", "WORK_MANAGER", "RELATIONSHIP_MANAGER"}) String roleType,
        @Schema(description = "이 담당자에게 배정된 항목의 제목/할 일 목록") List<RecipientInviteTaskResponse> tasks,
        @Schema(description = "공개 범위", example = "RELATIONSHIP", allowableValues = {"FAMILY", "WORK", "RELATIONSHIP"}) String disclosureScope,
        @Schema(description = "대체 담당자 여부") boolean isBackup,
        @Schema(description = "수락 상태", example = "PENDING", allowableValues = {"PENDING", "ACCEPTED", "DECLINED", "EXPIRED"}) String acceptanceStatus,
        @Schema(description = "초대 이메일이 발송된 주소") String email,
        @Schema(description = "초대 링크 만료 시각") LocalDateTime expiresAt,
        @Schema(description = "문의 주소") String contactEmail
) {
    public static RecipientInviteResponse of(Recipient recipient, String ownerName,
                                              List<RecipientInviteTaskResponse> tasks, String contactEmail) {
        return new RecipientInviteResponse(
                recipient.getName(),
                ownerName,
                recipient.getRoleType().name(),
                tasks,
                recipient.getDisclosureScope() == null ? null : recipient.getDisclosureScope().name(),
                Boolean.TRUE.equals(recipient.getIsBackup()),
                recipient.getAcceptanceStatus().name(),
                recipient.getEmail(),
                recipient.getInviteTokenExpiresAt(),
                contactEmail
        );
    }
}
