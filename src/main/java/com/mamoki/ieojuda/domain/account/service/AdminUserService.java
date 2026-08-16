package com.mamoki.ieojuda.domain.account.service;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// issue #56 "정지 상태" - 운영관리자가 계정을 정지/복구. 정지 시 세션을 전부 폐기해 이미 발급된
// Access Token도 만료 전에 즉시 거부되게 한다(JwtAuthenticationFilter가 매 요청마다 상태를 대조).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;
    private final SessionRevocationService sessionRevocationService;

    @Transactional
    public void suspend(Long userId) {
        User user = findUser(userId);
        user.suspend();
        sessionRevocationService.revokeAllSessions(user);
    }

    @Transactional
    public void reactivate(Long userId) {
        User user = findUser(userId);
        user.reactivate();
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
