package com.mamoki.ieojuda.global.util;

import jakarta.servlet.http.HttpServletRequest;

// rate limit / 로그인 실패 감사 - 프록시 뒤에서 도는 배포 환경을 고려해 X-Forwarded-For를 우선 사용한다
public class ClientIpResolver {

    private static final String FORWARDED_HEADER = "X-Forwarded-For";

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_HEADER);
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
