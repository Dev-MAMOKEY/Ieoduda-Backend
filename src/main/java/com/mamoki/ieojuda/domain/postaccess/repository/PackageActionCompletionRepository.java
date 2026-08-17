package com.mamoki.ieojuda.domain.postaccess.repository;

import com.mamoki.ieojuda.domain.postaccess.entity.PackageActionCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PackageActionCompletionRepository extends JpaRepository<PackageActionCompletion, Long> {

    // 역할별 패키지 조회 - 이 단계에서 이미 완료된 행동 전체 (진행률 계산용)
    List<PackageActionCompletion> findByHandoverStage_StageId(Long stageId);

    // 행동 완료 처리 - 같은 행동을 다시 완료 요청했는지 확인
    Optional<PackageActionCompletion> findByHandoverStage_StageIdAndItemId(Long stageId, Long itemId);

    // issue #78 - 단계 완료 판정(전체 항목 수 대비 완료된 항목 수)에 사용
    long countByHandoverStage_StageId(Long stageId);
}
