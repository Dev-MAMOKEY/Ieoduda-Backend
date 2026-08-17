package com.mamoki.ieojuda.domain.postaccess.repository;

import com.mamoki.ieojuda.domain.postaccess.entity.AccessToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccessTokenRepository extends JpaRepository<AccessToken, Long> {

    // "사후 인계 이메일" 화면 진입 - 링크의 토큰으로 열람 대상을 찾기 위한 조회
    Optional<AccessToken> findByTokenHash(String tokenHash);

    // OTP 인증 성공 후 발급된 열람 세션 식별자로 조회 (역할별 사후 패키지 조회에서 사용)
    Optional<AccessToken> findBySessionTokenHash(String sessionTokenHash);
}
