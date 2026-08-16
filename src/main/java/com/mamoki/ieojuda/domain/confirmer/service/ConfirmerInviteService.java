package com.mamoki.ieojuda.domain.confirmer.service;

import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerDecisionRequest;
import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerDecisionResponse;
import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerInviteResponse;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.email.token.TokenValidator;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.ratelimit.PublicLinkAuditor;
import com.mamoki.ieojuda.global.ratelimit.TokenLookupGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;

// 명세서 "지정확인자 수락 이메일/화면" - 확인자가 초대 링크로 진입해 역할을 확인하고 수락/거절 (로그인 불필요, 토큰이 곧 인증)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConfirmerInviteService {

    private final ConfirmerRepository confirmerRepository;
    private final AppProperties appProperties;
    private final TokenLookupGuard tokenLookupGuard;
    private final PublicLinkAuditor publicLinkAuditor;

    // 초대 조회 - 만료된 링크는 상태를 EXPIRED로 반영하고 차단, 이미 수락/거절한 경우는 현재 상태를 그대로 보여준다
    @Transactional
    public ConfirmerInviteResponse getInvite(String plainToken) {
        Confirmer confirmer = findByToken(plainToken);
        checkNotExpired(confirmer);

        String ownerName = confirmer.getPlan().getUser().getName();
        return ConfirmerInviteResponse.of(confirmer, ownerName, appProperties.getContactEmail());
    }

    // 역할 수락
    @Transactional
    public ConfirmerDecisionResponse accept(String plainToken, ConfirmerDecisionRequest request) {
        Confirmer confirmer = findByToken(plainToken);
        checkNotExpired(confirmer);
        checkPending(confirmer);

        confirmer.accept(inquiryOf(request));
        return ConfirmerDecisionResponse.from(confirmer);
    }

    // 역할 거절
    @Transactional
    public ConfirmerDecisionResponse decline(String plainToken, ConfirmerDecisionRequest request) {
        Confirmer confirmer = findByToken(plainToken);
        checkNotExpired(confirmer);
        checkPending(confirmer);

        confirmer.decline(inquiryOf(request));
        return ConfirmerDecisionResponse.from(confirmer);
    }

    // 문의 사항은 선택 입력이라 요청 바디 자체가 없을 수 있다
    private String inquiryOf(ConfirmerDecisionRequest request) {
        return request == null ? null : request.inquiry();
    }

    private Confirmer findByToken(String plainToken) {
        return tokenLookupGuard.resolve(plainToken,
                () -> confirmerRepository.findByInviteToken(TokenProvider.hashToken(plainToken)));
    }

    private void checkNotExpired(Confirmer confirmer) {
        Instant expiresAt = confirmer.getInviteTokenExpiresAt() == null
                ? null
                : confirmer.getInviteTokenExpiresAt().atZone(ZoneId.systemDefault()).toInstant();
        if (TokenValidator.isExpired(expiresAt, Instant.now())) {
            confirmer.expire();
            publicLinkAuditor.recordStateFailure(ErrorCode.ACCESS_LINK_EXPIRED);
            throw new CustomException(ErrorCode.ACCESS_LINK_EXPIRED);
        }
    }

    // 수락/거절은 대기 중(PENDING) 상태에서만 가능 - 이미 처리된 링크의 재사용을 차단
    private void checkPending(Confirmer confirmer) {
        if (confirmer.getAcceptanceStatus() != AcceptanceStatus.PENDING) {
            publicLinkAuditor.recordStateFailure(ErrorCode.ACCESS_LINK_ALREADY_USED);
            throw new CustomException(ErrorCode.ACCESS_LINK_ALREADY_USED);
        }
    }
}
