package com.mamoki.ieojuda.domain.recipient.repository;

import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipientRepository extends JpaRepository<Recipient, Long> {

    // 계정 삭제 시 이 계획에 등록된 담당자를 전부 지우기 위한 조회
    List<Recipient> findByPlan_PlanId(Long planId);
}
