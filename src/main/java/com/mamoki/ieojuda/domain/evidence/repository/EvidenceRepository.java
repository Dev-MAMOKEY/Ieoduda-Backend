package com.mamoki.ieojuda.domain.evidence.repository;

import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    // "공식 증빙 자료 제출" 화면 - 사건당 최대 3개 제한 검증용
    long countByReleaseCase_CaseId(Long caseId);
}
