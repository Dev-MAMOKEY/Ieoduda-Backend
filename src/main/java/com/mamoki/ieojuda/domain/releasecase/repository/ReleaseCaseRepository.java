package com.mamoki.ieojuda.domain.releasecase.repository;

import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReleaseCaseRepository extends JpaRepository<ReleaseCase, Long> {

    // 계획당 진행 중인 사건은 하나뿐 - 작성자 본인용 대기상태 조회, 사망 신고 시 중복 생성 방지에 사용
    Optional<ReleaseCase> findFirstByPlan_PlanIdOrderByCaseIdDesc(Long planId);

    // 스케줄러 - 대기 기간이 끝났고 동결되지 않은 사건을 찾기 위한 조회
    List<ReleaseCase> findByStatusAndFrozenFalseAndWaitingEndsAtLessThanEqual(ReleaseCaseStatus status, LocalDateTime now);

    // 계정 삭제 시 이 계획에 쌓인 사건(취소/완료된 과거 이력 포함)을 전부 지우기 위한 조회
    List<ReleaseCase> findByPlan_PlanId(Long planId);
}
