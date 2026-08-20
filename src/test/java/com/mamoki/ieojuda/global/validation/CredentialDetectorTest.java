package com.mamoki.ieojuda.global.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// issue #81 완료 조건 - "금지정보나 근거 없는 항목이 있으면 봉인이 차단된다"의 판정 로직.
// md 스펙 #91 오탐 대응 기준: 키워드 단독이 아니라 "키워드 + 값 형태"만 걸러야 한다.
class CredentialDetectorTest {

    @Test
    void containsCredential_whenKeywordFollowedByValue_returnsTrue() {
        assertThat(CredentialDetector.containsCredential("비밀번호는 abcd1234야")).isTrue();
        assertThat(CredentialDetector.containsCredential("PIN 5678")).isTrue();
        assertThat(CredentialDetector.containsCredential("복구코드: XY12-9988")).isTrue();
        assertThat(CredentialDetector.containsCredential("password=hunter2!")).isTrue();
    }

    @Test
    void containsCredential_whenKeywordAloneWithoutValue_returnsFalse() {
        assertThat(CredentialDetector.containsCredential("비밀번호는 지수가 알고 있어요")).isFalse();
        assertThat(CredentialDetector.containsCredential("PIN 번호는 따로 전달할게요")).isFalse();
    }

    @Test
    void containsCredential_whenNoKeyword_returnsFalse() {
        assertThat(CredentialDetector.containsCredential("인스타그램 계정을 비공개로 전환해줘")).isFalse();
        assertThat(CredentialDetector.containsCredential(null)).isFalse();
        assertThat(CredentialDetector.containsCredential("")).isFalse();
    }
}
