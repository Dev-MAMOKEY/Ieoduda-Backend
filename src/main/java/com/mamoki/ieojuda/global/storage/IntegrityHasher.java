package com.mamoki.ieojuda.global.storage;

import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class IntegrityHasher {

    private static final int BUFFER_SIZE = 8192;

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

    // 파일 전체를 힙에 올리지 않고 스트림을 순회하며 해시를 계산한다 - 검사(매직바이트/악성코드)와
    // 같은 패스에서 함께 계산하기 위한 용도로, 스트림을 소비하되 닫지는 않는다(호출자가 닫는다).
    public static String sha256Hex(InputStream content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = content.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
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
