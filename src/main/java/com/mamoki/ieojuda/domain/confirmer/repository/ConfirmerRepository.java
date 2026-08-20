package com.mamoki.ieojuda.domain.confirmer.repository;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.entity.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;

public interface ConfirmerRepository extends JpaRepository<Confirmer, UUID> {
    boolean existsByPlan_PlanIdAndEmail(UUID planId, String email);

    // 역할 점검 화면 칩 목록
    List<Confirmer> findByPlan_PlanIdOrderByConfirmIdAsc(UUID planId);

    // 사망 신고 접수 시, 같은 계획의 다른 확인자가 이미 신고했는지 대조하기 위한 조회
    List<Confirmer> findByPlan_PlanIdAndConfirmIdNotAndReportStatus(UUID planId, UUID excludeConfirmId, ReportStatus reportStatus);

    // issue #45 - "이 사건에 증빙을 내야 하는 확인자가 몇 명인가"를 판단하기 위한 조회. 매칭된(MATCHED)
    // 확인자만 그 사건의 증빙 제출·승인 대상이다(사망신고 매칭 시점에 정확히 두 명이 MATCHED로 전이함).
    List<Confirmer> findByPlan_PlanIdAndReportStatus(UUID planId, ReportStatus reportStatus);
}
