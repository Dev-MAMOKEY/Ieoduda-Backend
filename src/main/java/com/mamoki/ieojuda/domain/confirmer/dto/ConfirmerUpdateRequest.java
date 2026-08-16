package com.mamoki.ieojuda.domain.confirmer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// "확인자 수정" 화면 - 이름/이메일 수정
public record ConfirmerUpdateRequest(
        @Schema(description = "확인자 이름", example = "김민수")
        @NotBlank(message = "확인자 이름을 입력해 주세요.")
        @jakarta.validation.constraints.Size(max = 100, message = "확인자 이름은 100자 이하여야 합니다.") String name,

        @Schema(description = "확인자 이메일", example = "confirmer@example.com")
        @NotBlank(message = "확인자 이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @jakarta.validation.constraints.Size(max = 255, message = "확인자 이메일은 255자 이하여야 합니다.") String email
) {
}
