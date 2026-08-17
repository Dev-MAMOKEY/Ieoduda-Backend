package com.mamoki.ieojuda.global.scan;

// 악성코드 검사 결과. clean이 false면 detail에 탐지된 시그니처/사유를 담는다.
public record ScanResult(boolean clean, String detail) {

    public static ScanResult passed() {
        return new ScanResult(true, null);
    }

    public static ScanResult infected(String detail) {
        return new ScanResult(false, detail);
    }
}
