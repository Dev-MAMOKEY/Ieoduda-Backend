package com.mamoki.ieojuda.domain.securitytoken.service;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.entity.DisputeContact;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityToken;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.repository.SecurityTokenRepository;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.ratelimit.TokenLookupGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;

// issue #41 - 목적별 보안 토큰의 발급·검증·소비·폐기를 한 곳에 모은다. 만료·사용·폐기·목적 불일치
// 검증을 여기서 공통화해서, 각 도메인 서비스(사망신고/증빙제출/이의제기/역할수락)가 직접
// Confirmer.inviteToken 같은 필드를 조회하지 않고 이 서비스만 거치게 한다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecurityTokenService {

    private final SecurityTokenRepository securityTokenRepository;
    private final TokenLookupGuard tokenLookupGuard;

    @Transactional
    public String issueForConfirmer(SecurityTokenPurpose purpose, Confirmer confirmer, ReleaseCase releaseCase, LocalDateTime expiresAt) {
        String plainToken = TokenProvider.generatePlainToken();
        securityTokenRepository.save(SecurityToken.builder()
                .tokenHash(TokenProvider.hashToken(plainToken))
                .purpose(purpose)
                .confirmer(confirmer)
                .releaseCase(releaseCase)
                .expiresAt(expiresAt)
                .build());
        return plainToken;
    }

    @Transactional
    public String issueForRecipient(SecurityTokenPurpose purpose, Recipient recipient, LocalDateTime expiresAt) {
        String plainToken = TokenProvider.generatePlainToken();
        securityTokenRepository.save(SecurityToken.builder()
                .tokenHash(TokenProvider.hashToken(plainToken))
                .purpose(purpose)
                .recipient(recipient)
                .expiresAt(expiresAt)
                .build());
        return plainToken;
    }

    @Transactional
    public String issueForDisputeContact(SecurityTokenPurpose purpose, DisputeContact disputeContact, ReleaseCase releaseCase, LocalDateTime expiresAt) {
        String plainToken = TokenProvider.generatePlainToken();
        securityTokenRepository.save(SecurityToken.builder()
                .tokenHash(TokenProvider.hashToken(plainToken))
                .purpose(purpose)
                .disputeContact(disputeContact)
                .releaseCase(releaseCase)
                .expiresAt(expiresAt)
                .build());
        return plainToken;
    }

    // 작성자는 계획당 1명이라 별도 대상 필드 없이 releaseCase 바인딩만으로 충분히 특정된다 (예: CANCEL_CASE)
    @Transactional
    public String issueForCase(SecurityTokenPurpose purpose, ReleaseCase releaseCase, LocalDateTime expiresAt) {
        String plainToken = TokenProvider.generatePlainToken();
        securityTokenRepository.save(SecurityToken.builder()
                .tokenHash(TokenProvider.hashToken(plainToken))
                .purpose(purpose)
                .releaseCase(releaseCase)
                .expiresAt(expiresAt)
                .build());
        return plainToken;
    }

    // 조회 + 만료·사용·폐기·목적 불일치 검증 (아직 소비하지 않음 - 실제 처리가 성공한 뒤 consume()을 별도 호출)
    public SecurityToken resolve(String plainToken, SecurityTokenPurpose expectedPurpose) {
        return resolve(plainToken, EnumSet.of(expectedPurpose));
    }

    // waiting 페이지처럼 하나의 링크를 CANCEL_CASE(작성자)·RAISE_OBJECTION(이의 제기 연락처) 중
    // 어느 쪽이 눌러도 조회는 되어야 하는 경우 - 여러 목적을 한 번에 허용한다. 검증 로직은 resolve()와 동일.
    public SecurityToken resolveAny(String plainToken, java.util.Set<SecurityTokenPurpose> expectedPurposes) {
        return resolve(plainToken, expectedPurposes);
    }

    private SecurityToken resolve(String plainToken, java.util.Set<SecurityTokenPurpose> expectedPurposes) {
        SecurityToken token = tokenLookupGuard.resolve(plainToken,
                () -> securityTokenRepository.findByTokenHash(TokenProvider.hashToken(plainToken)));

        if (!expectedPurposes.contains(token.getPurpose())) {
            throw new CustomException(ErrorCode.TOKEN_PURPOSE_MISMATCH);
        }
        if (token.isRevoked()) {
            throw new CustomException(ErrorCode.TOKEN_REVOKED);
        }
        if (token.isUsed()) {
            throw new CustomException(ErrorCode.ACCESS_LINK_ALREADY_USED);
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new CustomException(ErrorCode.ACCESS_LINK_EXPIRED);
        }
        return token;
    }

    // 원자적 단일 사용 소비 - 동시 요청 중 정확히 하나만 성공한다
    @Transactional
    public void consume(SecurityToken token) {
        int updated = securityTokenRepository.markUsedIfUnused(token.getTokenId(), LocalDateTime.now());
        if (updated == 0) {
            throw new CustomException(ErrorCode.ACCESS_LINK_ALREADY_USED);
        }
    }

    // 재발급·연락처변경 시 대상의 미사용 토큰을 전부 폐기
    @Transactional
    public void revokeAllForConfirmer(Confirmer confirmer, SecurityTokenPurpose purpose) {
        securityTokenRepository.findByConfirmer_ConfirmIdAndPurposeAndUsedAtIsNullAndRevokedAtIsNull(confirmer.getConfirmId(), purpose)
                .forEach(SecurityToken::revoke);
    }

    @Transactional
    public void revokeAllForRecipient(Recipient recipient, SecurityTokenPurpose purpose) {
        securityTokenRepository.findByRecipient_AssigneeIdAndPurposeAndUsedAtIsNullAndRevokedAtIsNull(recipient.getAssigneeId(), purpose)
                .forEach(SecurityToken::revoke);
    }

    @Transactional
    public void revokeAllForDisputeContact(DisputeContact disputeContact, SecurityTokenPurpose purpose) {
        securityTokenRepository.findByDisputeContact_ContactIdAndPurposeAndUsedAtIsNullAndRevokedAtIsNull(disputeContact.getContactId(), purpose)
                .forEach(SecurityToken::revoke);
    }

    // 사건 종료(취소/완료) 시 그 사건에 묶인 미사용 토큰을 전부 폐기
    @Transactional
    public void revokeAllForCase(ReleaseCase releaseCase, SecurityTokenPurpose purpose) {
        securityTokenRepository.findByReleaseCase_CaseIdAndPurposeAndUsedAtIsNullAndRevokedAtIsNull(releaseCase.getCaseId(), purpose)
                .forEach(SecurityToken::revoke);
    }
}
