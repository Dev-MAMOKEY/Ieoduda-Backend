package com.mamoki.ieojuda.domain.postaccess.repository;

import com.mamoki.ieojuda.domain.postaccess.entity.PackageIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;

public interface PackageIssueRepository extends JpaRepository<PackageIssue, UUID> {

    // 계정 삭제 시 이 계획의 패키지 문제 신고를 전부 지우기 위한 조회 (items/role_assignees보다 먼저 지워야 함)
    List<PackageIssue> findByRecipient_Plan_PlanId(UUID planId);
}
