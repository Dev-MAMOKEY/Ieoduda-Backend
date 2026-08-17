package com.mamoki.ieojuda.global.security;

import java.util.UUID;

import com.mamoki.ieojuda.domain.account.entity.AdminPermission;
import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// issue #59 - ROLE_ADMIN/ROLE_EXTERNAL 하나로 뭉쳐 있던 권한을 업무 단위로 세분화해서 검사한다.
// SecurityConfig의 role 기반 URL 게이팅은 그대로 두고(1차 방어선), 이 가드는 서비스 계층에서
// "그 역할 중에서도 이 업무를 할 수 있는 사람인지"를 최소 권한 원칙에 따라 한 번 더 검사한다(2차 방어선).
@Component
@RequiredArgsConstructor
public class PermissionGuard {

    private final UserRepository userRepository;

    public User require(UUID userId, AdminPermission permission) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!user.hasPermission(permission)) {
            throw new CustomException(ErrorCode.INSUFFICIENT_PERMISSION);
        }
        return user;
    }
}
