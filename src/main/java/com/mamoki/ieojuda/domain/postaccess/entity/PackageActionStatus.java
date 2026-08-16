package com.mamoki.ieojuda.domain.postaccess.entity;

// "역할별 사후 패키지" 화면의 박스 우측 배지. DB에 저장하지 않고 completedAt과 실행 순서로 매번 계산한다
public enum PackageActionStatus {
    COMPLETED,   // 완료
    IN_PROGRESS, // 진행 중 (미완료 항목 중 실행 순서가 가장 앞선 하나)
    PENDING      // 대기 (앞 항목이 끝나야 열림)
}
