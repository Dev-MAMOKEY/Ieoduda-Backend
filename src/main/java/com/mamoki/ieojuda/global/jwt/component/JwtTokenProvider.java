package com.mamoki.ieojuda.global.jwt.component;

import com.mamoki.ieojuda.global.jwt.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

// 회원가입/로그인 - Access/Refresh 토큰 발급과 파싱을 담당.
// 여기서 던지는 io.jsonwebtoken.ExpiredJwtException / JwtException은
// GlobalExceptionHandler가 이미 TOKEN_EXPIRED / TOKEN_INVALID로 잡아서 응답하므로 별도 try-catch 없이 그대로 던진다.
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String TOKEN_TYPE_ACCESS = "ACCESS";
    private static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private final JwtProperties jwtProperties;

    private SecretKey key;

    @PostConstruct
    void init() {
        this.key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String email) {
        return generateToken(userId, email, TOKEN_TYPE_ACCESS, jwtProperties.getAccessTokenExpirationMs());
    }

    public String generateRefreshToken(Long userId) {
        return generateToken(userId, null, TOKEN_TYPE_REFRESH, jwtProperties.getRefreshTokenExpirationMs());
    }

    //토큰 -> 사용자 ID 추출
    public Long getUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public boolean isRefreshToken(String token) { // RT 검증
        return TOKEN_TYPE_REFRESH.equals(parseClaims(token).get(CLAIM_TOKEN_TYPE, String.class));
    }

    public boolean isAccessToken(String token) { // AT 검증 - JwtAuthenticationFilter에서 사용
        return TOKEN_TYPE_ACCESS.equals(parseClaims(token).get(CLAIM_TOKEN_TYPE, String.class));
    }

    //토큰 열기
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    //토큰 생성
    private String generateToken(Long userId, String email, String tokenType, long expirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(now)
                .expiration(expiry);

        if (email != null) {
            builder.claim("email", email);
        }

        return builder.signWith(key).compact();
    }
}
