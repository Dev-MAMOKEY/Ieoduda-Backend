package com.mamoki.ieojuda.domain.postaccess.repository;

import com.mamoki.ieojuda.domain.postaccess.entity.AccessToken;

import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccessTokenRepository extends JpaRepository<AccessToken, UUID> {

    // "사후 인계 이메일" 화면 진입 - 링크의 토큰으로 열람 대상을 찾기 위한 조회.
    // OTP 인증 성공 후에는 이 같은 토큰이 열람 세션 식별자로도 쓰인다(verifiedAt으로 세션 유효성 판단).
    Optional<AccessToken> findByTokenHash(String tokenHash);

    // OTP 발송 전용 - "쿨다운 확인 후 발송 시각 갱신" 사이의 경쟁 조건을 막기 위해 행에 비관적 잠금을 건다.
    // 동시에 들어온 요청 중 하나가 커밋될 때까지 나머지는 대기했다가, 갱신된 otpSentAt으로 쿨다운을 다시
    // 확인하게 되어 중복 발송을 막는다 (ReleaseCaseRepository.findByIdForUpdate와 같은 패턴).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccessToken a where a.tokenHash = :tokenHash")
    Optional<AccessToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    // 계정 삭제 - handover_stages를 지우기 전에 이 단계를 참조하는 토큰을 먼저 지워야 FK 위반이 안 난다
    void deleteByHandoverStage_StageId(UUID stageId);
}
