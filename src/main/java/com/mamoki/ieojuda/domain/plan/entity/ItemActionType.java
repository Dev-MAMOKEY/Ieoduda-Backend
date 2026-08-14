package com.mamoki.ieojuda.domain.plan.entity;

// "실행 순서 점검" 화면의 순서 충돌 판정에 쓰는 행동 분류
public enum ItemActionType {
    DELETE,   // 계정/자료를 삭제·탈퇴·정리·초기화하는 항목
    TRANSFER, // 자료·정보를 누군가에게 전달·인계·공개하는 항목
    OTHER     // 위 둘에 해당하지 않는 항목 (알림 발송 등) - 순서 충돌 판정 대상에서 제외
}
