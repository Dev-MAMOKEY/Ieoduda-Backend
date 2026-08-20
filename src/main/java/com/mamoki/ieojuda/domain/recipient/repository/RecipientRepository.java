package com.mamoki.ieojuda.domain.recipient.repository;

import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface RecipientRepository extends JpaRepository<Recipient, UUID> {

    // 계정 삭제 시 이 계획에 등록된 담당자를 전부 지우기 위한 조회
    List<Recipient> findByPlan_PlanId(UUID planId);

    // 역할 점검 화면 목록 - 대체 담당자는 이 화면에 노출하지 않는다
    List<Recipient> findByPlan_PlanIdAndIsBackupFalseOrderByAssigneeIdAsc(UUID planId);

    // "단계 완료 / 대체 담당자" 화면 - 주 담당자에 대해 등록된 대체 담당자 조회
    Optional<Recipient> findByBackupFor_AssigneeId(UUID primaryRecipientId);
}
