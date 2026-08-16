package com.mamoki.ieojuda.domain.account.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Locale;

// issue #55 "유출 비밀번호 검사" - Have I Been Pwned의 k-anonymity range API를 사용해, 평문 비밀번호는
// 절대 네트워크로 보내지 않고 SHA-1 해시 앞 5자리만으로 유출 여부를 조회한다.
// 외부 서비스가 불가용하면 가입 자체를 막지 않는다(fail-open) - 이 검사는 부가적인 방어선이지,
// 회원가입의 핵심 경로를 이 외부 API 가용성에 종속시킬 정도로 중요하진 않다.
@Slf4j
@Component
public class PasswordBreachChecker {

    private static final String RANGE_API_URL = "https://api.pwnedpasswords.com/range/";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public boolean isBreached(String plainPassword) {
        String sha1Hex = sha1Hex(plainPassword);
        String prefix = sha1Hex.substring(0, 5);
        String suffix = sha1Hex.substring(5);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RANGE_API_URL + prefix))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("[Password Breach Check] 예상치 못한 응답 코드={}", response.statusCode());
                return false;
            }
            return response.body().lines().anyMatch(line -> line.regionMatches(true, 0, suffix, 0, suffix.length()));
        } catch (Exception e) {
            log.warn("[Password Breach Check] 조회 실패, 가입은 계속 진행함. cause={}", e.getMessage());
            return false;
        }
    }

    private String sha1Hex(String plainPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashBytes = digest.digest(plainPassword.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
