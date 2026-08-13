package com.mamoki.ieojuda.domain.plan.repository;

import com.mamoki.ieojuda.domain.plan.entity.LifeAreaMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LifeAreaMessageRepository extends JpaRepository<LifeAreaMessage, Long> {

    // OpenAI 호출용 - 이 대화 세션(Conversation) 안의 맥락 전체를 오래된 순으로 조회 (페이지네이션 없이 전부 필요)
    List<LifeAreaMessage> findByConversation_ConversationIdOrderByMessageIdAsc(Long conversationId);

    // 대화 작성 화면용 - 최신 턴부터 페이지 단위로 조회 (무한 스크롤)
    Slice<LifeAreaMessage> findByConversation_ConversationIdOrderByMessageIdDesc(Long conversationId, Pageable pageable);

    // 계정 삭제 시 이 계획에 속한 모든 대화 세션의 메세지를 한 번에 지우기 위한 조회
    List<LifeAreaMessage> findByConversation_Plan_PlanId(Long planId);
}
