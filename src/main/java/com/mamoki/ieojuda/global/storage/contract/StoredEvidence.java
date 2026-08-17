package com.mamoki.ieojuda.global.storage.contract;

// 저장이 끝난 뒤 돌려받는 결과 값. 무결성 해시는 호출자가 검사 패스에서 이미 계산해 갖고 있으므로
// 여기서는 다루지 않는다(같은 스트림을 두 번 읽지 않기 위함).
public record StoredEvidence(
        String storageKey, // S3에 실제로 저장된 위치
        long fileSize
) {
}
