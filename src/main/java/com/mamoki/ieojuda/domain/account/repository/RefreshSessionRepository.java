package com.mamoki.ieojuda.domain.account.repository;

import com.mamoki.ieojuda.domain.account.entity.RefreshSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, String> {

    // 로그아웃 / 이메일 변경 등 계정 전체 세션 폐기용
    List<RefreshSession> findByUser_UserIdAndRevokedAtIsNull(UUID userId);

    // 계정 삭제 시 FK 위반 방지용 - 폐기 여부와 무관하게 이 사용자의 세션 전부
    List<RefreshSession> findByUser_UserId(UUID userId);

    // 재사용 탐지 시 같은 lineage(family) 전체를 찾아 차단하기 위한 조회
    List<RefreshSession> findByFamilyIdAndRevokedAtIsNull(String familyId);
}
