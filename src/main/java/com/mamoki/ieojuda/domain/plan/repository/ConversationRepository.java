package com.mamoki.ieojuda.domain.plan.repository;

import com.mamoki.ieojuda.domain.plan.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    // 계정 삭제 시 이 계획에 속한 대화 세션을 전부 지우기 위한 조회
    List<Conversation> findByPlan_PlanId(Long planId);
}
