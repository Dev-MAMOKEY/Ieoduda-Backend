package com.mamoki.ieojuda.domain.recipient.service;

import com.mamoki.ieojuda.domain.recipient.dto.RecipientInviteDecisionRequest;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientInviteDecisionResponse;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientInviteResponse;
import com.mamoki.ieojuda.domain.recipient.dto.RecipientInviteTaskResponse;
import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityToken;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.ratelimit.PublicLinkAuditor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;

// 명세서 "역할 수락 이메일/화면" - 담당자가 초대 링크로 진입해 역할을 확인하고 수락/거절 (로그인 불필요, 토큰이 곧 인증)
// issue #41 - 수락/거절은 ACCEPT_ROLE 목적 토큰으로만 처리한다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipientInviteService {

    private final ItemRepository itemRepository;
    private final AppProperties appProperties;
    private final PublicLinkAuditor publicLinkAuditor;
    private final SecurityTokenService securityTokenService;

    // 초대 조회 - 만료·사용·폐기·목적 불일치 링크는 차단
    @Transactional
    public RecipientInviteResponse getInvite(String plainToken) {
        SecurityToken token = resolveAcceptToken(plainToken);
        Recipient recipient = token.getRecipient();

        UUID itemOwnerId = Boolean.TRUE.equals(recipient.getIsBackup()) && recipient.getBackupFor() != null
                ? recipient.getBackupFor().getAssigneeId()
                : recipient.getAssigneeId();
        List<RecipientInviteTaskResponse> tasks = itemRepository.findByRecipient_AssigneeIdOrderByItemIdAsc(itemOwnerId).stream()
                .map(RecipientInviteTaskResponse::from)
                .toList();

        String ownerName = recipient.getPlan().getUser().getName();
        return RecipientInviteResponse.of(recipient, ownerName, tasks, token.getExpiresAt(), appProperties.getContactEmail());
    }

    // 역할 수락
    @Transactional
    public RecipientInviteDecisionResponse accept(String plainToken, RecipientInviteDecisionRequest request) {
        SecurityToken token = resolveAcceptToken(plainToken);
        Recipient recipient = token.getRecipient();
        checkPending(recipient);

        recipient.accept(inquiryOf(request));
        securityTokenService.consume(token);
        return RecipientInviteDecisionResponse.from(recipient);
    }

    // 역할 거절
    @Transactional
    public RecipientInviteDecisionResponse decline(String plainToken, RecipientInviteDecisionRequest request) {
        SecurityToken token = resolveAcceptToken(plainToken);
        Recipient recipient = token.getRecipient();
        checkPending(recipient);

        recipient.decline(inquiryOf(request));
        securityTokenService.consume(token);
        return RecipientInviteDecisionResponse.from(recipient);
    }

    // 문의 사항은 선택 입력이라 요청 바디 자체가 없을 수 있다
    private String inquiryOf(RecipientInviteDecisionRequest request) {
        return request == null ? null : request.inquiry();
    }

    private SecurityToken resolveAcceptToken(String plainToken) {
        return securityTokenService.resolve(plainToken, SecurityTokenPurpose.ACCEPT_ROLE);
    }

    // 수락/거절은 대기 중(PENDING) 상태에서만 가능 - 이미 처리된 링크의 재사용을 차단
    private void checkPending(Recipient recipient) {
        if (recipient.getAcceptanceStatus() != AcceptanceStatus.PENDING) {
            publicLinkAuditor.recordStateFailure(ErrorCode.ACCESS_LINK_ALREADY_USED);
            throw new CustomException(ErrorCode.ACCESS_LINK_ALREADY_USED);
        }
    }
}
