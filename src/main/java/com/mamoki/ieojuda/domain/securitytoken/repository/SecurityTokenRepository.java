package com.mamoki.ieojuda.domain.securitytoken.repository;

import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityToken;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SecurityTokenRepository extends JpaRepository<SecurityToken, UUID> {

    Optional<SecurityToken> findByTokenHash(String tokenHash);

    // 재발급·연락처변경·사건종료 시 대상별 기존 토큰 일괄 폐기 대상 조회
    List<SecurityToken> findByConfirmer_ConfirmIdAndPurposeAndUsedAtIsNullAndRevokedAtIsNull(UUID confirmId, SecurityTokenPurpose purpose);

    List<SecurityToken> findByRecipient_AssigneeIdAndPurposeAndUsedAtIsNullAndRevokedAtIsNull(UUID assigneeId, SecurityTokenPurpose purpose);

    List<SecurityToken> findByDisputeContact_ContactIdAndPurposeAndUsedAtIsNullAndRevokedAtIsNull(UUID contactId, SecurityTokenPurpose purpose);

    // 사건 종료(취소/완료) 시 그 사건에 묶인 목적별 토큰을 일괄 폐기하기 위한 조회
    List<SecurityToken> findByReleaseCase_CaseIdAndPurposeAndUsedAtIsNullAndRevokedAtIsNull(UUID caseId, SecurityTokenPurpose purpose);

    // 사건/확인자/담당자/이의연락처에 묶인 토큰(FK 참조, 사용·폐기 여부 무관)을 전부 지워 그 대상
    // 삭제 시 FK 위반을 막는다 - 계정 삭제(UserService.deletePlanData)와 테스트 정리 둘 다에서 쓴다.
    @Modifying
    @Query("DELETE FROM SecurityToken t WHERE t.releaseCase.caseId = :caseId")
    void deleteByReleaseCase_CaseId(@Param("caseId") UUID caseId);

    @Modifying
    @Query("DELETE FROM SecurityToken t WHERE t.confirmer.confirmId = :confirmId")
    void deleteByConfirmer_ConfirmId(@Param("confirmId") UUID confirmId);

    @Modifying
    @Query("DELETE FROM SecurityToken t WHERE t.recipient.assigneeId = :assigneeId")
    void deleteByRecipient_AssigneeId(@Param("assigneeId") UUID assigneeId);

    @Modifying
    @Query("DELETE FROM SecurityToken t WHERE t.disputeContact.contactId = :contactId")
    void deleteByDisputeContact_ContactId(@Param("contactId") UUID contactId);

    // issue #41 - "토큰 소비를 원자적 단일 사용으로 처리" - 조회 후 갱신이 아니라 조건부 UPDATE 한 번으로
    // 처리해, 동시에 같은 토큰이 들어와도 정확히 하나만 영향받은 행(1)을 얻는다. 나머지는 0을 받아 거절된다.
    @Modifying
    @Query("UPDATE SecurityToken t SET t.usedAt = :usedAt WHERE t.tokenId = :tokenId AND t.usedAt IS NULL AND t.revokedAt IS NULL")
    int markUsedIfUnused(@Param("tokenId") UUID tokenId, @Param("usedAt") LocalDateTime usedAt);
}
