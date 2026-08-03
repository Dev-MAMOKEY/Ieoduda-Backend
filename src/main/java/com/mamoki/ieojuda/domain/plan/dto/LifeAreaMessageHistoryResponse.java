package com.mamoki.ieojuda.domain.plan.dto;

import java.util.List;

// 대화 작성 화면 - 무한 스크롤 응답 (messages는 항상 오래된 순으로 정렬해서 내려줌)
public record LifeAreaMessageHistoryResponse(
        List<LifeAreaMessageResponse> messages,
        boolean hasNext // true면 더 과거 메세지가 남아있음 (위로 스크롤 시 다음 페이지 요청)
) {
    public static LifeAreaMessageHistoryResponse of(List<LifeAreaMessageResponse> messages, boolean hasNext) {
        return new LifeAreaMessageHistoryResponse(messages, hasNext);
    }
}
