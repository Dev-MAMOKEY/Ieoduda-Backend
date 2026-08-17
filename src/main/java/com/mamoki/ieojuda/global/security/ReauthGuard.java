package com.mamoki.ieojuda.global.security;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// issue #59 - 사건 동결·증빙 판정 같은 고위험 조작은 로그인 세션이 살아있다는 것만으로는 부족하다고 보고,
// 실행 직전 비밀번호를 다시 확인시킨다(방법 A: 재인증). 실패하면 아무 상태도 바꾸지 않고 그대로 막는다.
@Component
@RequiredArgsConstructor
public class ReauthGuard {

    private final PasswordEncoder passwordEncoder;

    public void verify(User user, String password) {
        if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new CustomException(ErrorCode.REAUTH_FAILED);
        }
    }
}
