package com.mamoki.ieojuda.domain.account.repository;

import com.mamoki.ieojuda.domain.account.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // 마이페이지 이메일 변경 - 본인을 제외한 다른 사용자가 이미 쓰는 이메일인지 확인
    boolean existsByEmailAndUserIdNot(String email, Long userId);
}
