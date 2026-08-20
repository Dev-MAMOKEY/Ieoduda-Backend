package com.mamoki.ieojuda.domain.securitytoken.entity;

// issue #41 - 생전 역할 수락 링크가 사망 신고·증빙 제출·이의 제기의 영구 접근키로
// 재사용되지 않도록, 토큰마다 목적을 하나로 못박는다.
public enum SecurityTokenPurpose {
    ACCEPT_ROLE,      // 역할 담당자·지정 확인자 수락/거절
    REPORT_DEATH,     // 지정 확인자의 사망 신고
    UPLOAD_EVIDENCE,  // 지정 확인자의 공식 증빙 제출
    RAISE_OBJECTION,  // 이의 제기 연락처의 이의 제기
    CANCEL_CASE       // 작성자 본인의 취소 (사건 바인딩만으로 특정 - 계획당 작성자 1명)
}
