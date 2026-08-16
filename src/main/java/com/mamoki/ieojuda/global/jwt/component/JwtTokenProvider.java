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
import java.util.UUID;

// 회원가입/로그인 - Access/Refresh 토큰 발급과 파싱을 담당.
// 여기서 던지는 io.jsonwebtoken.ExpiredJwtException / JwtException은
// GlobalExceptionHandler가 이미 TOKEN_EXPIRED / TOKEN_INVALID로 잡아서 응답하므로 별도 try-catch 없이 그대로 던진다.
//
// issue #56 - 모든 토큰에 jti(고유 ID), iss/aud(발급자/대상)를 싣는다. Access Token에는 추가로 발급 당시
// 사용자의 tokenVersion을 실어서, 필터가 DB의 현재 tokenVersion과 대조해 즉시 폐기 여부를 판단할 수 있게 한다.
// Refresh Token의 jti는 그 토큰에 대응하는 RefreshSession의 sessionId 그 자체다(회전/재사용탐지 조회에 사용).
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String CLAIM_TOKEN_VERSION = "ver";
    private static final String TOKEN_TYPE_ACCESS = "ACCESS";
    private static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private final JwtProperties jwtProperties;

    private SecretKey key;

    @PostConstruct
    void init() {
        this.key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String email, String role, Integer tokenVersion) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getAccessTokenExpirationMs());

        return baseBuilder(now, expiry)
                .subject(String.valueOf(userId))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .claim("email", email)
                .claim("role", role)
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .signWith(key)
                .compact();
    }

    // sessionId를 그대로 이 토큰의 jti로 사용 - refresh() 시 jti만으로 대응하는 RefreshSession을 바로 조회한다
    public String generateRefreshToken(Long userId, String sessionId, long expirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return baseBuilder(now, expiry)
                .id(sessionId)
                .subject(String.valueOf(userId))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .signWith(key)
                .compact();
    }

    private io.jsonwebtoken.JwtBuilder baseBuilder(Date issuedAt, Date expiry) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(jwtProperties.getIssuer())
                .audience().add(jwtProperties.getAudience()).and()
                .issuedAt(issuedAt)
                .expiration(expiry);
    }

    //토큰 -> 사용자 ID 추출
    public Long getUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    // 토큰 -> 권한(USER/ADMIN/EXTERNAL) 추출 - 참고용. 실제 인가는 필터가 DB의 현재 role을 신뢰한다(#56).
    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    // Refresh Token의 jti = 대응하는 RefreshSession.sessionId
    public String getJti(String token) {
        return parseClaims(token).getId();
    }

    public Integer getTokenVersion(String token) {
        return parseClaims(token).get(CLAIM_TOKEN_VERSION, Integer.class);
    }

    public boolean isRefreshToken(String token) { // RT 검증
        return TOKEN_TYPE_REFRESH.equals(parseClaims(token).get(CLAIM_TOKEN_TYPE, String.class));
    }

    public boolean isAccessToken(String token) { // AT 검증 - JwtAuthenticationFilter에서 사용
        return TOKEN_TYPE_ACCESS.equals(parseClaims(token).get(CLAIM_TOKEN_TYPE, String.class));
    }

    //토큰 열기 - 서명뿐 아니라 발급자(iss)/대상(aud)도 함께 검증
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(jwtProperties.getIssuer())
                .requireAudience(jwtProperties.getAudience())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
