package com.mamoki.ieojuda.domain.plan.entity;

public enum LifeAreaCategory {
    FAMILY,               // 가족
    RELATIONSHIP_CLEANUP, // 관계 정리
    WORK_CONTINUITY       // 업무 연속성

    ;

    public String label() {
        return switch (this) {
            case FAMILY -> "전달 메세지";
            case RELATIONSHIP_CLEANUP -> "관계 정리";
            case WORK_CONTINUITY -> "업무 처리";
        };
    }
}
