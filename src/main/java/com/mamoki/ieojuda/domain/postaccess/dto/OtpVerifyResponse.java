package com.mamoki.ieojuda.domain.postaccess.dto;

import com.mamoki.ieojuda.domain.postaccess.entity.AccessToken;
import io.swagger.v3.oas.annotations.media.Schema;

// "사후 인계 이메일" 화면 - "이메일 링크 열기" 클릭 시 다음 화면 분기용
// isBackup=false면 "역할별 사후 패키지"(7-2), true면 "단계별 대체 담당자"(7-3)로 프론트가 이동한다
public record OtpVerifyResponse(
        @Schema(description = "사후 접근 세션 ID") Long accessSessionId,
        @Schema(description = "대체 담당자 여부 - 다음 화면 분기 기준") boolean isBackup,
        @Schema(description = "발송 단계 ID") Long stageId,
        @Schema(description = "사후 인계 사건 ID") Long caseId
) {
    public static OtpVerifyResponse from(AccessToken token) {
        var stage = token.getHandoverStage();
        return new OtpVerifyResponse(
                token.getTokenId(),
                Boolean.TRUE.equals(stage.getRecipient().getIsBackup()),
                stage.getStageId(),
                stage.getReleaseCase().getCaseId()
        );
    }
}
