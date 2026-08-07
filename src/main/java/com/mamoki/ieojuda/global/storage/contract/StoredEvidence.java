package com.mamoki.ieojuda.global.storage.contract;

// 저장이 끝난 뒤 돌려받는 결과 값
public record StoredEvidence(
        String storageKey, // S3에 실제로 저장된 위치
        String integrityHash, // SHA-256 해시
        long fileSize
) {
}
