package com.mamoki.ieojuda.domain.plan.repository;

import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LifeAreaRepository extends JpaRepository<LifeArea, Long> {

    // 계정 삭제 시 이 계획에 속한 삶의 구역을 전부 지우기 위한 조회
    List<LifeArea> findByPlan_PlanId(Long planId);
}
