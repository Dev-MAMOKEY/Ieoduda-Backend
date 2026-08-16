package com.mamoki.ieojuda.domain.evidence.repository;

import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    // "공식 증빙 자료 제출" 화면 - 사건당 최대 3개 제한 검증용
    long countByReleaseCase_CaseId(Long caseId);

    // 계정 삭제 시 이 계획에 제출된 증빙을 전부 지우기 위한 조회 (S3 원본도 같이 정리해야 함)
    List<Evidence> findByPlan_PlanId(Long planId);
}
