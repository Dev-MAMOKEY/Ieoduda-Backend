package com.mamoki.ieojuda.global.security;

import com.mamoki.ieojuda.domain.account.entity.AdminPermission;
import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// issue #59 회귀 테스트 - ROLE_ADMIN/ROLE_EXTERNAL 하나만으로는 세부 업무 권한이 없으면 막혀야 한다.
class PermissionGuardTest {

    private UserRepository userRepository;
    private PermissionGuard permissionGuard;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        permissionGuard = new PermissionGuard(userRepository);
    }

    @Test
    void require_whenUserHasThePermission_returnsTheUser() {
        User admin = mock(User.class);
        when(admin.hasPermission(AdminPermission.CASE_SUPERVISE)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        User result = permissionGuard.require(1L, AdminPermission.CASE_SUPERVISE);

        assertThat(result).isSameAs(admin);
    }

    @Test
    void require_whenUserLacksThePermission_throwsInsufficientPermission() {
        User admin = mock(User.class);
        when(admin.hasPermission(AdminPermission.CASE_SUPERVISE)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> permissionGuard.require(1L, AdminPermission.CASE_SUPERVISE))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_PERMISSION));
    }

    @Test
    void require_whenUserDoesNotExist_throwsUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissionGuard.require(99L, AdminPermission.AUDIT_VIEW))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }
}
