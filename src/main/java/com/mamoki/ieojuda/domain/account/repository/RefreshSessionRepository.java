package com.mamoki.ieojuda.domain.account.repository;

import com.mamoki.ieojuda.domain.account.entity.RefreshSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, String> {

    // 로그아웃 / 이메일 변경 등 계정 전체 세션 폐기용
    List<RefreshSession> findByUser_UserIdAndRevokedAtIsNull(Long userId);

    // 재사용 탐지 시 같은 lineage(family) 전체를 찾아 차단하기 위한 조회
    List<RefreshSession> findByFamilyIdAndRevokedAtIsNull(String familyId);
}
