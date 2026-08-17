package com.mamoki.ieojuda.domain.plan.dto;

import java.util.UUID;

import com.mamoki.ieojuda.domain.confirmer.entity.DisputeContact;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import io.swagger.v3.oas.annotations.media.Schema;

// "대기 이의제기 수정" 화면 - 지금 저장된 대기/이의제기 설정 조회 (본인경고이메일 + 이의제기연락처 + 대기기간 한 번에)
public record ReleaseSettingsResponse(
        @Schema(description = "계획 ID") UUID planId,
        @Schema(description = "대기 기간(일), 아직 설정 안 했으면 null") Integer waitingDays,
        @Schema(description = "본인 경고 이메일, 아직 등록 안 했으면 null") String selfWarningEmail,
        @Schema(description = "본인 경고 이메일 검증 완료 여부") boolean selfWarningEmailVerified,
        @Schema(description = "이의 제기 연락처, 아직 등록 안 했으면 null") DisputeContactSummary disputeContact
) {
    public record DisputeContactSummary(
            @Schema(description = "이의 제기 연락처 ID") UUID contactId,
            @Schema(description = "이름") String name,
            @Schema(description = "이메일") String email,
            @Schema(description = "검증 완료 여부") boolean verified
    ) {
        public static DisputeContactSummary from(DisputeContact contact) {
            return new DisputeContactSummary(
                    contact.getContactId(),
                    contact.getName(),
                    contact.getEmail(),
                    Boolean.TRUE.equals(contact.getIsVerified())
            );
        }
    }

    public static ReleaseSettingsResponse of(Plan plan, DisputeContact disputeContact) {
        return new ReleaseSettingsResponse(
                plan.getPlanId(),
                plan.getWaitingDays(),
                plan.getSelfWarningEmail(),
                Boolean.TRUE.equals(plan.getSelfWarningEmailVerified()),
                disputeContact == null ? null : DisputeContactSummary.from(disputeContact)
        );
    }
}
