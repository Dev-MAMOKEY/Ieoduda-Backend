package com.mamoki.ieojuda.domain.postaccess.dto;

import com.mamoki.ieojuda.domain.postaccess.entity.AccessToken;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// "사후 인계 이메일" 화면 - 링크 검증 직후 조회. 인증 전이라 인계 내용은 담지 않는다
public record PosthumousAccessResponse(
        @Schema(description = "담당자 이름", example = "이지수") String assigneeName,
        @Schema(description = "작성자(계획 소유자) 이름", example = "김나무") String ownerName,
        @Schema(description = "역할 유형", example = "RELATIONSHIP_MANAGER", allowableValues = {"FAMILY_MANAGER", "WORK_MANAGER", "RELATIONSHIP_MANAGER"}) String roleType,
        @Schema(description = "초대 이메일이 발송된 주소") String email,
        @Schema(description = "링크 만료 시각") LocalDateTime expiresAt,
        @Schema(description = "문의 주소") String contactEmail
) {
    public static PosthumousAccessResponse of(AccessToken token, String contactEmail) {
        Recipient recipient = token.getHandoverStage().getRecipient();
        String ownerName = token.getHandoverStage().getPlan().getUser().getName();
        return new PosthumousAccessResponse(
                recipient.getName(),
                ownerName,
                recipient.getRoleType().name(),
                recipient.getEmail(),
                token.getExpiresAt(),
                contactEmail
        );
    }
}
