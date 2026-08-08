package com.mamoki.ieojuda.global.storage;

import java.util.UUID;
import java.util.regex.Pattern;

public class StorageKeyGenerator {

    private static final Pattern SAFE_EXTENSION = Pattern.compile("^[a-zA-Z0-9]{1,10}$");

    private StorageKeyGenerator() {
        // static 메서드만 존재 하는 클래스 이므로 인스턴스화 방지
    }

    // 원본 파일명은 키에 넣지 않는다 (경로 조작·개인 정보 노출 방지). 파일명은 DB 컬럼에만 보관한다.
    public static String generate(Long caseId, String fileName) {
        String extension = extractSafeExtension(fileName);
        String uuid = UUID.randomUUID().toString();
        return extension.isEmpty()
                ? "evidence/%d/%s".formatted(caseId, uuid) // 확장자 없음
                : "evidence/%d/%s.%s".formatted(caseId, uuid, extension); // 확장자 있음
    }


    // 확장자 추출 매세드
    private static String extractSafeExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        String extension = fileName.substring(dotIndex + 1);
        return SAFE_EXTENSION.matcher(extension).matches() ? extension : "";
    }
}
