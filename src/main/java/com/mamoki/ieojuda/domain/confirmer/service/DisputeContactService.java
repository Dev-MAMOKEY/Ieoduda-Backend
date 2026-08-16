package com.mamoki.ieojuda.domain.confirmer.service;

import com.mamoki.ieojuda.domain.confirmer.dto.DisputeContactRegisterRequest;
import com.mamoki.ieojuda.domain.confirmer.dto.DisputeContactResponse;
import com.mamoki.ieojuda.domain.confirmer.entity.DisputeContact;
import com.mamoki.ieojuda.domain.confirmer.repository.DisputeContactRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.service.PlanOwnershipReader;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.contract.EmailSendResult;
import com.mamoki.ieojuda.global.email.sender.EmailSender;
import com.mamoki.ieojuda.global.email.template.EmailBuilder;
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
import java.time.LocalDateTime;
import java.time.ZoneId;

// 명세서 "대기 이의제기 설정" 화면 - 이의 제기 연락처 등록 + 이메일 검증
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisputeContactService {

    private final PlanOwnershipReader planOwnershipReader;
    private final DisputeContactRepository disputeContactRepository;
    private final EmailSender emailSender;
    private final AppProperties appProperties;
    private final TokenLookupGuard tokenLookupGuard;
    private final PublicLinkAuditor publicLinkAuditor;

    @Transactional
    public DisputeContactResponse register(Long userId, Long planId, DisputeContactRegisterRequest request) {
        Plan plan = planOwnershipReader.findOwnedPlan(userId, planId);

        DisputeContact contact = disputeContactRepository.save(DisputeContact.builder()
                .plan(plan)
                .name(request.name())
                .email(request.email())
                .build());

        boolean emailSent = issueTokenAndSendVerificationEmail(contact);
        return DisputeContactResponse.of(contact, emailSent);
    }

    // "대기 이의제기 수정" 화면 - 이미 등록된 연락처의 이름/이메일 수정. 이메일이 바뀌면 검증을 다시 받아야 하므로 재발송한다.
    @Transactional
    public DisputeContactResponse update(Long userId, Long planId, Long contactId, DisputeContactRegisterRequest request) {
        planOwnershipReader.findOwnedPlan(userId, planId);
        DisputeContact contact = findContact(planId, contactId);
        boolean emailChanged = !contact.getEmail().equals(request.email());

        contact.updateContact(request.name(), request.email());

        boolean emailSent = false;
        if (emailChanged) {
            contact.resetVerification();
            emailSent = issueTokenAndSendVerificationEmail(contact);
        }

        return DisputeContactResponse.of(contact, emailSent);
    }

    private boolean issueTokenAndSendVerificationEmail(DisputeContact contact) {
        String plainToken = TokenProvider.generatePlainToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(appProperties.getInviteTokenTtlHours());
        contact.issueInviteToken(TokenProvider.hashToken(plainToken), expiresAt);

        String secureLink = appProperties.getBaseUrl() + "/dispute-contacts/verify/" + plainToken;
        EmailContent content = EmailBuilder.build(
                "이의 제기 연락처",
                "링크를 눌러 이의 제기 연락처로 등록되었음을 확인해 주세요.",
                expiresAt.atZone(ZoneId.systemDefault()),
                secureLink,
                appProperties.getContactEmail()
        );
        return emailSender.send(contact.getEmail(), content).success();
    }

    private DisputeContact findContact(Long planId, Long contactId) {
        DisputeContact contact = disputeContactRepository.findById(contactId)
                .orElseThrow(() -> new CustomException(ErrorCode.DISPUTE_CONTACT_NOT_FOUND));
        if (!contact.getPlan().getPlanId().equals(planId)) {
            throw new CustomException(ErrorCode.DISPUTE_CONTACT_NOT_FOUND);
        }
        return contact;
    }

    // 검증 링크 클릭 - 로그인 불필요(토큰 자체가 증명)
    @Transactional
    public void verify(String plainToken) {
        DisputeContact contact = tokenLookupGuard.resolve(plainToken,
                () -> disputeContactRepository.findByInviteToken(TokenProvider.hashToken(plainToken)));

        if (Boolean.TRUE.equals(contact.getIsVerified())) {
            return; // 이미 검증됨 - 링크 재클릭은 그냥 성공 처리
        }

        Instant expiresAt = contact.getInviteTokenExpiresAt().atZone(ZoneId.systemDefault()).toInstant();
        if (TokenValidator.isExpired(expiresAt, Instant.now())) {
            publicLinkAuditor.recordStateFailure(ErrorCode.ACCESS_LINK_EXPIRED);
            throw new CustomException(ErrorCode.ACCESS_LINK_EXPIRED);
        }

        contact.verify();
    }
}
