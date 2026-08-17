package com.mamoki.ieojuda.domain.postaccess.dto;

import com.mamoki.ieojuda.domain.postaccess.entity.AccessToken;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.entity.RoleType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// "사후 인계 이메일" 화면 - 링크 진입 시 본인 확인 전에 보여줄 최소 정보. 인계 내용은 절대 포함하지 않는다.
public record PosthumousAccessResponse(
        @Schema(description = "역할 담당자 이름", example = "이지수") String recipientName,
        @Schema(description = "작성자(계획 소유자) 이름", example = "김나무") String authorName,
        @Schema(description = "역할 종류") String roleType,
        @Schema(description = "역할 한글 명칭", example = "관계 정리 담당자") String roleLabel,
        @Schema(description = "링크 만료 시각") LocalDateTime expiresAt,
        @Schema(description = "OTP 발송 여부") boolean otpSent,
        @Schema(description = "문의 주소") String contactEmail
) {
    public static PosthumousAccessResponse of(AccessToken accessToken, String contactEmail) {
        Recipient recipient = accessToken.getHandoverStage().getRecipient();
        return new PosthumousAccessResponse(
                recipient.getName(),
                recipient.getPlan().getUser().getName(),
                recipient.getRoleType().name(),
                roleLabel(recipient.getRoleType()),
                accessToken.getExpiresAt(),
                accessToken.getOtpSentAt() != null,
                contactEmail
        );
    }

    private static String roleLabel(RoleType roleType) {
        return switch (roleType) {
            case FAMILY_MANAGER -> "가족 담당자";
            case WORK_MANAGER -> "업무 담당자";
            case RELATIONSHIP_MANAGER -> "관계 정리 담당자";
        };
    }
}
