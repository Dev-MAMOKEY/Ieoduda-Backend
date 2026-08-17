package com.mamoki.ieojuda.global.scan;

import java.util.Optional;

public class FileSignatureDetector {

    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private FileSignatureDetector() {
        // static 메서드만 존재 하는 클래스 이므로 인스턴스화 방지
    }

    // 클라이언트가 보낸 Content-Type이 아니라 파일 앞부분의 실제 바이트로 형식을 판별한다.
    // 매칭되는 시그니처가 없으면 빈 값을 반환한다(허용 목록 밖 형식이거나 위장된 파일).
    public static Optional<String> detect(byte[] header) {
        if (matches(header, PDF_SIGNATURE)) {
            return Optional.of("application/pdf");
        }
        if (matches(header, JPEG_SIGNATURE)) {
            return Optional.of("image/jpeg");
        }
        if (matches(header, PNG_SIGNATURE)) {
            return Optional.of("image/png");
        }
        return Optional.empty();
    }

    private static boolean matches(byte[] header, byte[] signature) {
        if (header == null || header.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (header[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
