package com.mamoki.ieojuda.global.storage;

import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class IntegrityHasher {

    private IntegrityHasher() {
        // static 메서드만 존재 하는 클래스 이므로 인스턴스화 방지
    }

    // 증빙 원본 바이트의 무결성 해시. DB에는 이 값만 저장하고 원본은 저장하지 않는다.
    public static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content);
            return toHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new CustomException(ErrorCode.EVIDENCE_STORAGE_FAILED);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
