package com.mamoki.ieojuda.domain.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// 명세서 "새 계획 만들기" 화면 - 계획 이름/대기기간과 구역별 초기 선택값을 "세부사항 대화하기" 버튼 한 번에 같이 제출
public record PlanCreateRequest(
        // TODO: 로그인/JWT 인증이 붙으면 인증된 사용자로 대체하고 이 필드는 제거한다.
        @Schema(description = "작성자 user_id (임시 - 인증 붙으면 제거 예정)", example = "1")
        @NotNull(message = "작성자 정보가 필요합니다.") Long userId,
        @Schema(description = "계획 이름", example = "내 유고 계획")
        @NotBlank(message = "계획 이름을 입력해 주세요.") String name,
        @Schema(description = "사후 공개 대기 기간(일), 7~30 사이", example = "14")
        @NotNull(message = "대기 기간을 입력해 주세요.") Integer waitingDays,

        @Schema(description = "가족·친구·지인에게 전달할 내용", example = "가족사진은 클라우드 보관함 2번째 폴더에 있어요.")
        String familyMessage,

        @Schema(description = "SNS 계정 처리", example = "PRIVATE")
        SnsAction snsAction,
        @Schema(description = "SNS 처리 '기타' 선택 시 상세 내용")
        String snsOtherDetail,
        @Schema(description = "메신저·연락처로 부고 전달 방식", example = "CLOSE_FRIEND")
        ObituaryDelivery obituaryDelivery,

        @Schema(description = "업무 계정·이메일 처리", example = "HANDOVER")
        WorkAccountAction workAccountAction,
        @Schema(description = "진행 중인 일·거래처 인계 여부", example = "HANDOVER")
        OngoingWorkHandover ongoingWorkHandover
) {
}
