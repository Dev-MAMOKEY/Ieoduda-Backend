package com.mamoki.ieojuda.domain.confirmer.dto;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// "지정확인자 수락 이메일" 화면 - 초대 링크로 진입했을 때 보여줄 내용
public record ConfirmerInviteResponse(
        @Schema(description = "확인자 이름", example = "유지민") String confirmerName,
        @Schema(description = "작성자(계획 소유자) 이름", example = "김나무") String ownerName,
        @Schema(description = "수락 상태", example = "PENDING", allowableValues = {"PENDING", "ACCEPTED", "DECLINED", "EXPIRED"}) String acceptanceStatus,
        @Schema(description = "초대 이메일이 발송된 주소") String email,
        @Schema(description = "초대 링크 만료 시각") LocalDateTime expiresAt,
        @Schema(description = "문의 주소") String contactEmail
) {
    public static ConfirmerInviteResponse of(Confirmer confirmer, String ownerName, LocalDateTime expiresAt, String contactEmail) {
        return new ConfirmerInviteResponse(
                confirmer.getName(),
                ownerName,
                confirmer.getAcceptanceStatus().name(),
                confirmer.getEmail(),
                expiresAt,
                contactEmail
        );
    }
}
