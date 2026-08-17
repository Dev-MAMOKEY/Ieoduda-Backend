package com.mamoki.ieojuda.domain.confirmer.dto;

import java.util.UUID;

import com.mamoki.ieojuda.domain.confirmer.entity.DisputeContact;
import io.swagger.v3.oas.annotations.media.Schema;

public record DisputeContactResponse(
        @Schema(description = "이의 제기 연락처 ID") UUID contactId,
        @Schema(description = "이름") String name,
        @Schema(description = "이메일") String email,
        @Schema(description = "검증 완료 여부") boolean verified,
        @Schema(description = "검증 메일 발송 성공 여부") boolean emailSent
) {
    public static DisputeContactResponse of(DisputeContact contact, boolean emailSent) {
        return new DisputeContactResponse(
                contact.getContactId(),
                contact.getName(),
                contact.getEmail(),
                Boolean.TRUE.equals(contact.getIsVerified()),
                emailSent
        );
    }
}
