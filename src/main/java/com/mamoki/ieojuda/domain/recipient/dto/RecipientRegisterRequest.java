package com.mamoki.ieojuda.domain.recipient.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// "AI 구조화 결과 검토"에서 승인된 항목 하나에 배정할 역할 담당자 입력
public record RecipientRegisterRequest(
        @Schema(description = "담당자를 배정할 승인된 항목 ID", example = "1")
        @NotNull(message = "항목을 선택해 주세요.") UUID itemId,

        @Schema(description = "담당자 이름", example = "김민수")
        @NotBlank(message = "담당자 이름을 입력해 주세요.")
        @jakarta.validation.constraints.Size(max = 100, message = "담당자 이름은 100자 이하여야 합니다.") String name,

        @Schema(description = "담당자 이메일", example = "recipient@example.com")
        @NotBlank(message = "담당자 이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @jakarta.validation.constraints.Size(max = 255, message = "담당자 이메일은 255자 이하여야 합니다.") String email,

        @Schema(description = "최대 단계 대기 시간(시간 단위, 7~30일)", example = "168")
        @NotNull(message = "대기 기간을 선택해 주세요.")
        @Min(value = 168, message = "대기 기간은 7일 이상이어야 합니다.")
        @Max(value = 720, message = "대기 기간은 30일 이하여야 합니다.") Integer maxWaitHours,

        @Schema(description = "대체 담당자 (선택). [대체 담당자 등록하기]로 입력한 경우에만 전달")
        @Valid BackupRegisterRequest backup
) {
}
