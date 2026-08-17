package com.mamoki.ieojuda.domain.recipient.service;

import com.mamoki.ieojuda.domain.recipient.dto.RecipientInviteDecisionRequest;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientInviteDecisionResponse;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientInviteResponse;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientInviteTaskResponse;
import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
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
import java.util.UUID;
import java.util.List;

// 명세서 "역할 수락 이메일/화면" - 담당자가 초대 링크로 진입해 역할을 확인하고 수락/거절 (로그인 불필요, 토큰이 곧 인증)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipientInviteService {

    private final RecipientRepository recipientRepository;
    private final ItemRepository itemRepository;
    private final AppProperties appProperties;
    private final TokenLookupGuard tokenLookupGuard;
    private final PublicLinkAuditor publicLinkAuditor;

    // 초대 조회 - 만료된 링크는 상태를 EXPIRED로 반영하고 차단, 이미 수락/거절한 경우는 현재 상태를 그대로 보여준다
    @Transactional
    public RecipientInviteResponse getInvite(String plainToken) {
        Recipient recipient = findByToken(plainToken);
        checkNotExpired(recipient);

        UUID itemOwnerId = Boolean.TRUE.equals(recipient.getIsBackup()) && recipient.getBackupFor() != null
                ? recipient.getBackupFor().getAssigneeId()
                : recipient.getAssigneeId();
        List<RecipientInviteTaskResponse> tasks = itemRepository.findByRecipient_AssigneeIdOrderByItemIdAsc(itemOwnerId).stream()
                .map(RecipientInviteTaskResponse::from)
                .toList();

        String ownerName = recipient.getPlan().getUser().getName();
        return RecipientInviteResponse.of(recipient, ownerName, tasks, appProperties.getContactEmail());
    }

    // 역할 수락
    @Transactional
    public RecipientInviteDecisionResponse accept(String plainToken, RecipientInviteDecisionRequest request) {
        Recipient recipient = findByToken(plainToken);
        checkNotExpired(recipient);
        checkPending(recipient);

        recipient.accept(inquiryOf(request));
        return RecipientInviteDecisionResponse.from(recipient);
    }

    // 역할 거절
    @Transactional
    public RecipientInviteDecisionResponse decline(String plainToken, RecipientInviteDecisionRequest request) {
        Recipient recipient = findByToken(plainToken);
        checkNotExpired(recipient);
        checkPending(recipient);

        recipient.decline(inquiryOf(request));
        return RecipientInviteDecisionResponse.from(recipient);
    }

    // 문의 사항은 선택 입력이라 요청 바디 자체가 없을 수 있다
    private String inquiryOf(RecipientInviteDecisionRequest request) {
        return request == null ? null : request.inquiry();
    }

    private Recipient findByToken(String plainToken) {
        return tokenLookupGuard.resolve(plainToken,
                () -> recipientRepository.findByInviteToken(TokenProvider.hashToken(plainToken)));
    }

    private void checkNotExpired(Recipient recipient) {
        Instant expiresAt = recipient.getInviteTokenExpiresAt() == null
                ? null
                : recipient.getInviteTokenExpiresAt().atZone(ZoneId.systemDefault()).toInstant();
        if (TokenValidator.isExpired(expiresAt, Instant.now())) {
            recipient.expire();
            publicLinkAuditor.recordStateFailure(ErrorCode.ACCESS_LINK_EXPIRED);
            throw new CustomException(ErrorCode.ACCESS_LINK_EXPIRED);
        }
    }

    // 수락/거절은 대기 중(PENDING) 상태에서만 가능 - 이미 처리된 링크의 재사용을 차단
    private void checkPending(Recipient recipient) {
        if (recipient.getAcceptanceStatus() != AcceptanceStatus.PENDING) {
            publicLinkAuditor.recordStateFailure(ErrorCode.ACCESS_LINK_ALREADY_USED);
            throw new CustomException(ErrorCode.ACCESS_LINK_ALREADY_USED);
        }
    }
}
