package com.mamoki.ieojuda.domain.recipient.entity;

public enum RoleType {
    FAMILY_MANAGER,       // 가족 담당자
    WORK_MANAGER,         // 업무 담당자
    RELATIONSHIP_MANAGER  // 관계 정리 담당자

    ;

    // 사후 인계 화면(#76,#77)에서 함께 쓰는 한글 표기 - 명세서 「역할별 최소 공개 정책」 표기와 일치
    public String label() {
        return switch (this) {
            case FAMILY_MANAGER -> "가족 담당자";
            case WORK_MANAGER -> "업무 담당자";
            case RELATIONSHIP_MANAGER -> "관계 정리 담당자";
        };
    }
}
