package com.mamoki.ieojuda.global.storage;

import java.util.Map;
import java.util.UUID;

public class StorageKeyGenerator {

    // 원본 파일명이 아니라 매직바이트로 판별된 MIME 타입에서 확장자를 뽑는다 - 클라이언트가 보낸
    // 파일명(예: evil.pdf 안에 PNG가 들어있는 경우)을 신뢰하면 실제 형식과 다른 확장자가 붙는다.
    private static final Map<String, String> EXTENSION_BY_MIME = Map.of(
            "application/pdf", "pdf",
            "image/jpeg", "jpg",
            "image/png", "png"
    );

    private StorageKeyGenerator() {
        // static 메서드만 존재 하는 클래스 이므로 인스턴스화 방지
    }

    // 원본 파일명은 키에 넣지 않는다 (경로 조작·개인 정보 노출 방지). 파일명은 DB 컬럼에만 보관한다.
    public static String generate(Long caseId, String detectedMimeType) {
        String extension = EXTENSION_BY_MIME.getOrDefault(detectedMimeType, "");
        String uuid = UUID.randomUUID().toString();
        return extension.isEmpty()
                ? "evidence/%d/%s".formatted(caseId, uuid) // 확장자 없음
                : "evidence/%d/%s.%s".formatted(caseId, uuid, extension); // 확장자 있음
    }
}
