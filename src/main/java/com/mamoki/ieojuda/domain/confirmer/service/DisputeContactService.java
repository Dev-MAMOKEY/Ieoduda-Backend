package com.mamoki.ieojuda.domain.confirmer.service;

import com.mamoki.ieojuda.domain.confirmer.dto.DisputeContactRegisterRequest;
import com.mamoki.ieojuda.domain.confirmer.dto.DisputeContactResponse;
import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.confirmer.entity.DisputeContact;
import com.mamoki.ieojuda.domain.confirmer.repository.DisputeContactRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.service.PlanOwnershipReader;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxService;
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
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import java.time.ZoneId;

// 명세서 "대기 이의제기 설정" 화면 - 이의 제기 연락처 등록 + 이메일 검증
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisputeContactService {

    private final PlanOwnershipReader planOwnershipReader;
    private final DisputeContactRepository disputeContactRepository;
    private final EmailOutboxService emailOutboxService;
    private final AppProperties appProperties;
    private final TokenLookupGuard tokenLookupGuard;
    private final PublicLinkAuditor publicLinkAuditor;
    private final SecurityTokenService securityTokenService;

    @Transactional
    public DisputeContactResponse register(UUID userId, UUID planId, DisputeContactRegisterRequest request) {
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
    public DisputeContactResponse update(UUID userId, UUID planId, UUID contactId, DisputeContactRegisterRequest request) {
        planOwnershipReader.findOwnedPlan(userId, planId);
        DisputeContact contact = findContact(planId, contactId);
        boolean emailChanged = !contact.getEmail().equals(request.email());

        contact.updateContact(request.name(), request.email());

        boolean emailSent = false;
        if (emailChanged) {
            contact.resetVerification();
            emailSent = issueTokenAndSendVerificationEmail(contact);
            // issue #41 - 연락처가 바뀌면 예전 연락처로 나간 이의 제기 토큰도 함께 폐기한다
            securityTokenService.revokeAllForDisputeContact(contact, SecurityTokenPurpose.RAISE_OBJECTION);
        }

        return DisputeContactResponse.of(contact, emailSent);
    }

    // 발송은 EmailOutboxScheduler가 비동기로 처리하므로, 여기서는 큐 등록 자체만 하고 항상 true를 반환한다
    // (실제 발송 결과는 "이메일 발송 감사" 화면에서 확인)
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
        emailOutboxService.enqueue(contact.getPlan(), null, EmailType.DISPUTE_CONTACT_VERIFICATION, contact.getEmail(), content);
        return true;
    }

    // "대기 이의제기 설정" 화면 - 검증 메일을 놓쳤을 때 다시 보내기. Recipient/Confirmer의 재발송과 동일 패턴
    // (플랫 경로 + 소유권은 plan.user로 직접 확인 - 노션 명세서가 /plans/{planId}/ 없이 /dispute-contacts/{id}/뿐이라 planId를 받지 않음)
    @Transactional
    public DisputeContactResponse resendVerificationEmail(UUID userId, UUID contactId) {
        DisputeContact contact = disputeContactRepository.findById(contactId)
                .orElseThrow(() -> new CustomException(ErrorCode.DISPUTE_CONTACT_NOT_FOUND));
        if (!contact.getPlan().getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.DISPUTE_CONTACT_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(contact.getIsVerified())) {
            throw new CustomException(ErrorCode.DISPUTE_CONTACT_RESEND_NOT_ALLOWED);
        }

        // issueInviteToken()이 기존 토큰 해시를 덮어쓰므로, 이전 토큰은 더 이상 조회되지 않아 자동으로 무효화된다
        boolean emailSent = issueTokenAndSendVerificationEmail(contact);
        return DisputeContactResponse.of(contact, emailSent);
    }

    private DisputeContact findContact(UUID planId, UUID contactId) {
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

    // issue #41 - 사건이 대기(WAITING) 상태로 전환되는 시점에, 검증을 마친 이의 제기 연락처에게
    // 이의 제기 전용 토큰(RAISE_OBJECTION, 사건 바인딩)을 발급해 안내 메일을 보낸다. 검증에 쓴 토큰을
    // 이의 제기까지 재사용하지 않기 위함이다 - PartnerReviewService가 증빙을 승인할 때 호출한다.
    @Transactional
    public void notifyVerifiedContactsOfObjectionWindow(ReleaseCase releaseCase) {
        List<DisputeContact> contacts = disputeContactRepository.findByPlan_PlanId(releaseCase.getPlan().getPlanId());
        for (DisputeContact contact : contacts) {
            if (!Boolean.TRUE.equals(contact.getIsVerified())) {
                continue;
            }
            LocalDateTime expiresAt = releaseCase.getWaitingEndsAt() != null
                    ? releaseCase.getWaitingEndsAt()
                    : LocalDateTime.now().plusDays(appProperties.getActiveTokenTtlDays());
            String plainToken = securityTokenService.issueForDisputeContact(
                    SecurityTokenPurpose.RAISE_OBJECTION, contact, releaseCase, expiresAt);

            String secureLink = appProperties.getBaseUrl() + "/waiting?caseId=" + releaseCase.getCaseId() + "&token=" + plainToken;
            EmailContent content = EmailBuilder.build(
                    "이의 제기 가능 기간 안내",
                    "잘못된 점이 있다면 링크로 들어가 이의를 제기해 주세요.",
                    expiresAt.atZone(ZoneId.systemDefault()),
                    secureLink,
                    appProperties.getContactEmail()
            );
            emailOutboxService.enqueue(contact.getPlan(), null, EmailType.OBJECTION_WINDOW_NOTICE, contact.getEmail(), content);
        }
    }
}
