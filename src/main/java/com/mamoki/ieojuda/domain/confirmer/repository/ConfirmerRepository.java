package com.mamoki.ieojuda.domain.confirmer.repository;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.entity.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface ConfirmerRepository extends JpaRepository<Confirmer, UUID> {
    boolean existsByPlan_PlanIdAndEmail(UUID planId, String email);

    // 역할 점검 화면 칩 목록
    List<Confirmer> findByPlan_PlanIdOrderByConfirmIdAsc(UUID planId);

    // "지정확인자 수락 이메일" 화면 진입 - 초대 링크의 토큰으로 확인자를 찾기 위한 조회
    Optional<Confirmer> findByInviteToken(String inviteToken);

    // 사망 신고 접수 시, 같은 계획의 다른 확인자가 이미 신고했는지 대조하기 위한 조회
    List<Confirmer> findByPlan_PlanIdAndConfirmIdNotAndReportStatus(UUID planId, UUID excludeConfirmId, ReportStatus reportStatus);
}
