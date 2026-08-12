package com.mamoki.ieojuda.domain.confirmer.repository;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfirmerRepository extends JpaRepository<Confirmer, Long> {
    boolean existsByPlan_PlanIdAndEmail(Long planId, String email);
}
