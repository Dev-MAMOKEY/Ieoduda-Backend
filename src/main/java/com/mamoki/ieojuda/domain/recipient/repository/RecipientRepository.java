package com.mamoki.ieojuda.domain.recipient.repository;

import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipientRepository extends JpaRepository<Recipient, Long> {
}
