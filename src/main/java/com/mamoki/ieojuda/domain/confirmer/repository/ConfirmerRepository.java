package com.mamoki.ieojuda.domain.confirmer.repository;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfirmerRepository extends JpaRepository<Confirmer, Long> {
    boolean existsByPlan_PlanIdAndEmail(Long planId, String email);

    // 역할 점검 화면 칩 목록
    List<Confirmer> findByPlan_PlanIdOrderByConfirmIdAsc(Long planId);

    // "지정확인자 수락 이메일" 화면 진입 - 초대 링크의 토큰으로 확인자를 찾기 위한 조회
    Optional<Confirmer> findByInviteToken(String inviteToken);
}
