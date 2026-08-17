package com.mamoki.ieojuda.domain.plan.repository;

import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.plan.entity.ItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.List;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    // 계획(케이스) 전체에 쌓인 항목 조회 - 카테고리(LifeArea)는 대화 세션마다 새로 생길 수 있어서
    // life_id 하나가 아니라 plan_id 기준으로 전체를 모아야 한다.
    List<Item> findByLifeArea_Plan_PlanIdOrderByItemIdAsc(UUID planId);

    // 대화 턴이 새로 끝날 때마다 아직 검토 안 된(PROPOSED) 이전 초안을 지우기 위한 조회
    List<Item> findByLifeArea_Plan_PlanIdAndStatus(UUID planId, ItemStatus status);

    // 역할 점검 상세 - 해당 담당자에게 배정된 항목
    List<Item> findByRecipient_AssigneeIdOrderByItemIdAsc(UUID assigneeId);

    // "실행 순서 점검" 화면 - 담당자가 배정된(=발송 대상이 확정된) 항목만 순서 점검 대상
    List<Item> findByLifeArea_Plan_PlanIdAndRecipientIsNotNullOrderBySortOrderAscItemIdAsc(UUID planId);

    // issue #90 - "담당자 누락"도 순서 점검 화면의 충돌 규칙 중 하나라, 담당자 없는 항목도 포함해야 한다
    List<Item> findByLifeArea_Plan_PlanIdOrderBySortOrderAscItemIdAsc(UUID planId);

    // issue #86 - "AI 구조화 결과 검토" 화면 새로고침용 목록 조회. status/disclosureScope는 없으면(null) 필터링하지 않음
    @Query("SELECT i FROM Item i WHERE i.lifeArea.plan.planId = :planId " +
            "AND (:status IS NULL OR i.status = :status) " +
            "AND (:disclosureScope IS NULL OR i.disclosureScope = :disclosureScope) " +
            "ORDER BY i.sortOrder ASC, i.itemId ASC")
    List<Item> findByPlanIdAndFilters(@Param("planId") UUID planId,
                                       @Param("status") ItemStatus status,
                                       @Param("disclosureScope") DisclosureScope disclosureScope);
}
