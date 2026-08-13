package com.mamoki.ieojuda.domain.confirmer.repository;

import com.mamoki.ieojuda.domain.confirmer.entity.DisputeContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DisputeContactRepository extends JpaRepository<DisputeContact, Long> {

    // 검증 링크 클릭 시 토큰으로 연락처를 찾기 위한 조회
    Optional<DisputeContact> findByInviteToken(String inviteToken);

    // "대기 이의제기 수정" 화면 조회용 - 계획당 이의 제기 연락처는 하나만 쓰므로 가장 최근 것 하나만 가져온다
    Optional<DisputeContact> findFirstByPlan_PlanIdOrderByContactIdDesc(Long planId);

    // 계정 삭제 시 이 계획에 등록된 이의 제기 연락처를 전부 지우기 위한 조회
    List<DisputeContact> findByPlan_PlanId(Long planId);
}
