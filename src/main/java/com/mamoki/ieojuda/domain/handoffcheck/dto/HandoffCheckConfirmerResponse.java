package com.mamoki.ieojuda.domain.handoffcheck.dto;

import java.util.UUID;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

// "선택형 생전 인계 점검" 화면 - 지정 확인자 한 명당 박스 하나
public record HandoffCheckConfirmerResponse(
        @Schema(description = "확인자 ID") UUID confirmId,
        @Schema(description = "확인자 이름") String name,
        @Schema(description = "수락 요청 이메일 발송 완료 여부") boolean isEmailSent,
        @Schema(description = "역할 수락 완료 여부") boolean isRoleAccepted,
        @Schema(description = "확인자가 수락/거절 시 남긴 문의 사항 - 없으면 null") String inquiry,
        @Schema(description = "준비 완료 여부 - 이메일 발송과 역할 수락이 모두 끝나면 true") boolean isReady
) {
    public static HandoffCheckConfirmerResponse from(Confirmer confirmer) {
        boolean isEmailSent = Boolean.TRUE.equals(confirmer.getInviteSent());
        boolean isRoleAccepted = confirmer.getAcceptanceStatus() == AcceptanceStatus.ACCEPTED;

        return new HandoffCheckConfirmerResponse(
                confirmer.getConfirmId(),
                confirmer.getName(),
                isEmailSent,
                isRoleAccepted,
                confirmer.getInquiry(),
                isEmailSent && isRoleAccepted
        );
    }
}
