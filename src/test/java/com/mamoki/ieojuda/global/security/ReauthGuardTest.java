package com.mamoki.ieojuda.global.security;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// issue #59 회귀 테스트 - 사건 동결·증빙 판정 같은 고위험 조작은 비밀번호 재확인 없이는 실행되지 않아야 한다.
class ReauthGuardTest {

    private PasswordEncoder passwordEncoder;
    private ReauthGuard reauthGuard;
    private User user;

    @BeforeEach
    void setUp() {
        passwordEncoder = mock(PasswordEncoder.class);
        reauthGuard = new ReauthGuard(passwordEncoder);
        user = mock(User.class);
        when(user.getPassword()).thenReturn("hashed");
    }

    @Test
    void verify_whenPasswordMatches_doesNotThrow() {
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);

        assertThatCode(() -> reauthGuard.verify(user, "correct")).doesNotThrowAnyException();
    }

    @Test
    void verify_whenPasswordDoesNotMatch_throwsReauthFailed() {
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> reauthGuard.verify(user, "wrong"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REAUTH_FAILED));
    }

    @Test
    void verify_whenPasswordIsNull_throwsReauthFailedWithoutCallingEncoder() {
        assertThatThrownBy(() -> reauthGuard.verify(user, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REAUTH_FAILED));
    }
}
