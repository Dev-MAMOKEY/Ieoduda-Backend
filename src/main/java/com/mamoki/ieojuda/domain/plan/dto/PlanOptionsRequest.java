package com.mamoki.ieojuda.domain.plan.dto;

// 새 계획 만들기 화면 - "세부사항 대화하기" 버튼으로 가족/관계정리/업무정리 3개 구역 선택값을 한 번에 전달
public record PlanOptionsRequest(
        String familyMessage,
        SnsAction snsAction,
        String snsOtherDetail,
        ObituaryDelivery obituaryDelivery,
        String closeFriendName,
        WorkAccountAction workAccountAction,
        OngoingWorkHandover ongoingWorkHandover,
        String handoverDetail
) {
}
