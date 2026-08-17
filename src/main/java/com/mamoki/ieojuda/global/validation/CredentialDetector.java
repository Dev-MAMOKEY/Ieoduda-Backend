package com.mamoki.ieojuda.global.validation;

import java.util.regex.Pattern;

// issue #81 - 패키지 봉인 시 금지정보(비밀번호·PIN·OTP·복구코드) 포함 여부 검사에 사용.
// "비밀번호는 지수가 알고 있어요"처럼 값이 없는 문장은 통과시켜야 하므로, 키워드 단독이 아니라
// "키워드 + 값 형태"(키워드 바로 뒤에 오는 영문/숫자/기호 토큰)를 함께 본다(md 스펙 #91 오탐 대응 기준).
// 대화 메시지 저장 시점·OpenAI 전송 직전 검증(issue #91)도 이 클래스를 재사용할 것.
public class CredentialDetector {

    private static final Pattern CREDENTIAL_PATTERN = Pattern.compile(
            "(비밀번호|패스워드|password|pin번?호?|otp|인증\\s?번호|복구\\s?코드|recovery\\s?code)"
                    + "\\s*[:=은는이가]?\\s*[a-zA-Z0-9!@#$%^&*_-]{4,}",
            Pattern.CASE_INSENSITIVE
    );

    private CredentialDetector() {
        // 인스턴스화 방지
    }

    public static boolean containsCredential(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return CREDENTIAL_PATTERN.matcher(text).find();
    }
}
