package com.mamoki.ieojuda.domain.postaccess.repository;

import com.mamoki.ieojuda.domain.postaccess.entity.AccessToken;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccessTokenRepository extends JpaRepository<AccessToken, UUID> {

    // "사후 인계 이메일" 화면 진입 - 링크의 토큰으로 열람 대상을 찾기 위한 조회.
    // OTP 인증 성공 후에는 이 같은 토큰이 열람 세션 식별자로도 쓰인다(verifiedAt으로 세션 유효성 판단).
    Optional<AccessToken> findByTokenHash(String tokenHash);
}
