package com.mamoki.ieojuda.domain.plan.entity;

public enum LifeAreaCategory {
    FAMILY,               // 가족
    RELATIONSHIP_CLEANUP, // 관계 정리
    WORK_CONTINUITY       // 업무 연속성

    ;

    // 계획 홈 화면(#84)에서 쓰는 구역별 고정 문구 - 백엔드-구현-스펙.md 3-2 예시 그대로.
    // (검토 필요) summary는 예시 문구를 그대로 가져온 것이라 실제로는 카테고리 설명이 아니라
    // 항목 내용 기반 동적 요약이어야 할 수도 있음 - 기획/디자인 확인 후 조정.
    public String label() {
        return switch (this) {
            case FAMILY -> "전달 메세지";
            case RELATIONSHIP_CLEANUP -> "관계 정리";
            case WORK_CONTINUITY -> "업무 처리";
        };
    }

    public String summary() {
        return switch (this) {
            case FAMILY -> "가족·친구·지인에게";
            case RELATIONSHIP_CLEANUP -> "SNS 계정·부고 전달";
            case WORK_CONTINUITY -> "디자인 프로젝트 인수인계";
        };
    }
}
