package com.mamoki.ieojuda.domain.account.repository;

import com.mamoki.ieojuda.domain.account.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
