package com.mamoki.ieojuda.domain.plan.repository;

import com.mamoki.ieojuda.domain.plan.entity.LifeAreaMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LifeAreaMessageRepository extends JpaRepository<LifeAreaMessage, Long> {

    // OpenAI 호출용 - 대화 맥락 전체를 오래된 순으로 조회 (페이지네이션 없이 전부 필요)
    List<LifeAreaMessage> findByLifeArea_LifeIdOrderByMessageIdAsc(Long lifeId);

    // 대화 작성 화면용 - 최신 턴부터 페이지 단위로 조회 (무한 스크롤)
    Slice<LifeAreaMessage> findByLifeArea_LifeIdOrderByMessageIdDesc(Long lifeId, Pageable pageable);
}
