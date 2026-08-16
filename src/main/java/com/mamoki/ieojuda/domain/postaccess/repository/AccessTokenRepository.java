package com.mamoki.ieojuda.domain.postaccess.repository;

import com.mamoki.ieojuda.domain.postaccess.entity.AccessToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccessTokenRepository extends JpaRepository<AccessToken, Long> {

    Optional<AccessToken> findByTokenHash(String tokenHash);
}
