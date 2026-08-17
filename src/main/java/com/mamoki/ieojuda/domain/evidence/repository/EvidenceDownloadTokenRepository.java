package com.mamoki.ieojuda.domain.evidence.repository;

import com.mamoki.ieojuda.domain.evidence.entity.EvidenceDownloadToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvidenceDownloadTokenRepository extends JpaRepository<EvidenceDownloadToken, UUID> {

    Optional<EvidenceDownloadToken> findByTokenHash(String tokenHash);

    // 계정/증빙 삭제 시 이 증빙에 발급된 다운로드 토큰을 함께 정리하기 위한 조회
    List<EvidenceDownloadToken> findByEvidence_EvidenceId(UUID evidenceId);

    // issue #43 - "1회성" 소비를 조건부 UPDATE 한 번으로 원자 처리 (issue #41의 SecurityToken과 같은 패턴)
    @Modifying
    @Query("UPDATE EvidenceDownloadToken t SET t.usedAt = :usedAt WHERE t.tokenId = :tokenId AND t.usedAt IS NULL")
    int markUsedIfUnused(@Param("tokenId") UUID tokenId, @Param("usedAt") LocalDateTime usedAt);
}
