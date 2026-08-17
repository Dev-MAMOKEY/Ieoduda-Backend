package com.mamoki.ieojuda.global.util;

public class EmailMasker {

    private EmailMasker() {
        // 인스턴스화 방지
    }

    // 로컬파트 앞 2글자만 남기고 나머지는 *** 처리 (예: jisoo@naver.com -> ji***@naver.com)
    public static String mask(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String visible = local.length() <= 2 ? local : local.substring(0, 2);
        return visible + "***@" + parts[1];
    }
}
