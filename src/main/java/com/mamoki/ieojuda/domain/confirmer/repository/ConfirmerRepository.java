package com.mamoki.ieojuda.domain.confirmer.repository;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConfirmerRepository extends JpaRepository<Confirmer, Long> {
    boolean existsByPlan_PlanIdAndEmail(Long planId, String email);

    // 역할 점검 화면 칩 목록
    List<Confirmer> findByPlan_PlanIdOrderByConfirmIdAsc(Long planId);
}
