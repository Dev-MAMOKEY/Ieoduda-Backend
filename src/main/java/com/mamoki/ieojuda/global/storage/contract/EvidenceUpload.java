package com.mamoki.ieojuda.global.storage.contract;

public record EvidenceUpload(
        String fileName,
        String mimeType,
        byte[] content
) {
}
