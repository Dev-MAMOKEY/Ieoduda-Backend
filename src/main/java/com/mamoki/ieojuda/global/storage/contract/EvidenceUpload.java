package com.mamoki.ieojuda.global.storage.contract;

import java.io.InputStream;
import java.util.function.Supplier;

// content는 InputStream을 직접 담지 않고 Supplier로 감싼다 - S3 SDK가 전송 실패 시 재시도하려면
// 스트림을 처음부터 다시 열어야 하는데, 멀티파트 임시 파일에서 얻은 InputStream은
// mark/reset을 지원하지 않아 한 번 읽으면 재시도가 불가능해진다. Supplier면 재시도마다 새로 연다.
public record EvidenceUpload(
        String fileName,
        String mimeType,
        Supplier<InputStream> content,
        long contentLength
) {
}
