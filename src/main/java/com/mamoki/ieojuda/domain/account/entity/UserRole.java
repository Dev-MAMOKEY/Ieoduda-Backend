package com.mamoki.ieojuda.domain.account.entity;

public enum UserRole {
    USER,     // 계획 작성자 - 공개 회원가입으로 생성됨
    ADMIN,    // 서비스 운영자 - DB에서 role만 승격, 공개 회원가입으로는 될 수 없음
    EXTERNAL  // 외부 법무·장례 파트너 - DB에서 role만 승격, 공개 회원가입으로는 될 수 없음
}
