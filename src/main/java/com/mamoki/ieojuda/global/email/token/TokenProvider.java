package com.mamoki.ieojuda.global.email.token;

import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class TokenProvider {

    private static final int TOKEN_BYTE_LENGTH = 32; // 256bit
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TokenProvider() {
        // static 메서드만 존재 하는 클래스 이므로 인스턴스화 방지
    }

    //발급용 평문 토큰 생성.

    public static String generatePlainToken() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes); // URL에서 문제가 발생하지 않도록 UTF-Safe Base 64 문자열로 인코딩
    }

    // OTP 4자리 코드 생성. 경우의 수가 적어(0000~9999) hashToken(SHA-256)이 아닌 PasswordEncoder로 별도 해싱한다 - 호출부 책임
    public static String generateOtpCode() {
        int code = SECURE_RANDOM.nextInt(10_000);
        return String.format("%04d", code);
    }

    /*
     * 평문 토큰을 SHA-256 hex 문자열로 해싱.
     * 이 값만 DB(invite_token 컬럼)에 저장, 대조한다.
     */
    public static String hashToken(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_TOKEN_FORMAT);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(plainToken.getBytes(StandardCharsets.UTF_8));
            return toHex(hashBytes); // 64자리 16진수로 변환
        } catch (NoSuchAlgorithmException e) {
            throw new CustomException(ErrorCode.TOKEN_HASHING_FAILED);
        }
    }

    private static String toHex(byte[] bytes) {  // hashBytes 형식을 문자열(16진수)로 반환
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}