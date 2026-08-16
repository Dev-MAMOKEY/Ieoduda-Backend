package com.mamoki.ieojuda.global.jwt.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@NoArgsConstructor
@Component
public class JwtProperties {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    // issue #56 - 토큰이 이 서비스가 발급한 게 맞는지(iss), 이 서비스를 대상으로 한 게 맞는지(aud) 검증하기 위한 값
    @Value("${jwt.issuer}")
    private String issuer;

    @Value("${jwt.audience}")
    private String audience;
}
