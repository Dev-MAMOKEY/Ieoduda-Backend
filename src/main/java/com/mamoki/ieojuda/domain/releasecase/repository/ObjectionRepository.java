package com.mamoki.ieojuda.domain.releasecase.repository;

import com.mamoki.ieojuda.domain.releasecase.entity.Objection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ObjectionRepository extends JpaRepository<Objection, Long> {

    // 계정 삭제 시 이 계획에 접수된 이의 제기를 전부 지우기 위한 조회
    List<Objection> findByPlan_PlanId(Long planId);
}
