package com.mamoki.ieojuda.domain.handoffcheck.service;

import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckRespondRequest;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckRespondResponse;
import com.mamoki.ieojuda.domain.handoffcheck.entity.HandoffCheckResponse;
import com.mamoki.ieojuda.domain.handoffcheck.repository.HandoffCheckResponseRepository;
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

// 명세서 "선택형 생전 인계 점검" - 담당자가 점검 메일의 링크로 진입해 역할 이해 여부를 응답 (로그인 불필요, 토큰이 곧 인증)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HandoffCheckRespondService {

    private final HandoffCheckResponseRepository handoffCheckResponseRepository;
    private final TokenLookupGuard tokenLookupGuard;
    private final PublicLinkAuditor publicLinkAuditor;

    @Transactional
    public HandoffCheckRespondResponse respond(String plainToken, HandoffCheckRespondRequest request) {
        HandoffCheckResponse response = findByToken(plainToken);
        checkNotExpired(response);
        checkNotAlreadyResponded(response);

        response.respond(request.emailReachable(), request.roleUnderstood(), request.scopeUnderstood(), request.inquiry());
        return HandoffCheckRespondResponse.from(response);
    }

    private HandoffCheckResponse findByToken(String plainToken) {
        return tokenLookupGuard.resolve(plainToken,
                () -> handoffCheckResponseRepository.findByInviteToken(TokenProvider.hashToken(plainToken)));
    }

    private void checkNotExpired(HandoffCheckResponse response) {
        Instant expiresAt = response.getInviteTokenExpiresAt() == null
                ? null
                : response.getInviteTokenExpiresAt().atZone(ZoneId.systemDefault()).toInstant();
        if (TokenValidator.isExpired(expiresAt, Instant.now())) {
            response.invalidateInviteToken();
            publicLinkAuditor.recordStateFailure(ErrorCode.ACCESS_LINK_EXPIRED);
            throw new CustomException(ErrorCode.ACCESS_LINK_EXPIRED);
        }
    }

    // 이미 응답한 링크의 재사용을 차단
    private void checkNotAlreadyResponded(HandoffCheckResponse response) {
        if (response.getRespondedAt() != null) {
            publicLinkAuditor.recordStateFailure(ErrorCode.ACCESS_LINK_ALREADY_USED);
            throw new CustomException(ErrorCode.ACCESS_LINK_ALREADY_USED);
        }
    }
}
