package com.mamoki.ieojuda.domain.confirmer.service;

import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerBulkRegisterRequest;
import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerBulkRegisterResponse;
import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerDetailResponse;
import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerRegisterRequest;
import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerRegisterResponse;
import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerResendResponse;
import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerUpdateRequest;
import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerUpdateResponse;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.service.PlanOwnershipReader;
import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxService;
import com.mamoki.ieojuda.global.email.template.EmailBuilder;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.List;
import java.util.Set;

// "지정 확인자 등록" 화면 - 확인자 이름/이메일을 한 번에 등록하고, 등록과 동시에 수락 이메일을 발송
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConfirmerService {

    private final PlanOwnershipReader planOwnershipReader;
    private final ConfirmerRepository confirmerRepository;
    private final EmailOutboxService emailOutboxService;
    private final AppProperties appProperties;
    private final SecurityTokenService securityTokenService;

    @Transactional
    // 특정 계획에 여러 명의 확인자를 한번에 등록하는 함수
    public ConfirmerBulkRegisterResponse registerAll(UUID userId, UUID planId, ConfirmerBulkRegisterRequest request) {
        Plan plan = planOwnershipReader.findOwnedPlan(userId, planId);
        validateAll(planId, request.confirmers());

        List<ConfirmerRegisterResponse> responses = new ArrayList<>();
        for (ConfirmerRegisterRequest confirmerRequest : request.confirmers()) {
            responses.add(registerOne(plan, confirmerRequest));
        }

        return new ConfirmerBulkRegisterResponse(responses);
    }

    // 이메일은 발송 후 되돌릴 수 없으므로, 저장·발송을 시작하기 전에 요청 전체를 먼저 검증한다
    private void validateAll(UUID planId, List<ConfirmerRegisterRequest> requests) {
        Set<String> requestedEmails = new HashSet<>();

        for (ConfirmerRegisterRequest request : requests) {
            // 같은 사람이 두 번 등록되면 "독립된 2인 확인" 원칙이 무력화되므로 이메일 중복을 차단
            if (!requestedEmails.add(request.email())
                    || confirmerRepository.existsByPlan_PlanIdAndEmail(planId, request.email())) {
                throw new CustomException(ErrorCode.CONFIRMER_ALREADY_REGISTERED);
            }
        }
    }

    // 확인자 저장 + 초대 토큰 발급 + 수락 이메일 발송 큐 등록
    // 실제 발송은 EmailOutboxScheduler가 비동기로 처리하므로, 여기서는 큐 등록 자체의 성공만 응답에 담는다
    // (실제 발송 결과는 "이메일 발송 감사" 화면에서 확인)
    private ConfirmerRegisterResponse registerOne(Plan plan, ConfirmerRegisterRequest request) {
        Confirmer confirmer = confirmerRepository.save(Confirmer.builder()
                .plan(plan)
                .name(request.name())
                .email(request.email())
                .build());

        issueInviteAndSend(confirmer);

        return ConfirmerRegisterResponse.of(confirmer, true, null);
    }

    // issue #41 - 재발급 시 이전에 발급됐던 미사용 ACCEPT_ROLE 토큰은 먼저 폐기한다(재발급·연락처변경 시 기존 토큰 폐기)
    private void issueInviteAndSend(Confirmer confirmer) {
        securityTokenService.revokeAllForConfirmer(confirmer, SecurityTokenPurpose.ACCEPT_ROLE);
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(appProperties.getInviteTokenTtlHours());
        String plainToken = securityTokenService.issueForConfirmer(SecurityTokenPurpose.ACCEPT_ROLE, confirmer, null, expiresAt);
        confirmer.markInviteSent();
        sendAcceptanceEmail(confirmer, plainToken, expiresAt);
    }

    private void sendAcceptanceEmail(Confirmer confirmer, String plainToken, LocalDateTime expiresAt) {
        String secureLink = appProperties.getBaseUrl() + "/confirmer-acceptances/" + plainToken;
        EmailContent content = EmailBuilder.build(
                "지정 확인자",
                "지정 확인자 역할 수락 여부를 확인해 주세요.",
                expiresAt.atZone(ZoneId.systemDefault()),
                secureLink,
                appProperties.getContactEmail()
        );
        emailOutboxService.enqueue(confirmer.getPlan(), null, EmailType.CONFIRMER_ACCEPTANCE_INVITE, confirmer.getEmail(), content);
    }

    // "역할 점검" 화면 상세 - 이름 클릭 시 해당 확인자 정보
    public ConfirmerDetailResponse getConfirmer(UUID userId, UUID planId, UUID confirmId) {
        planOwnershipReader.findOwnedPlan(userId, planId);
        Confirmer confirmer = findOwnedConfirmer(planId, confirmId);
        return ConfirmerDetailResponse.from(confirmer);
    }

    // "수락 요청 다시 보내기" - 이미 수락/거절한 확인자에게는 재전송하지 않는다
    @Transactional
    public ConfirmerResendResponse resendAcceptanceEmail(UUID userId, UUID confirmId) {
        Confirmer confirmer = confirmerRepository.findById(confirmId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONFIRMER_NOT_FOUND));
        if (!confirmer.getPlan().getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.CONFIRMER_NOT_FOUND);
        }
        if (confirmer.getAcceptanceStatus() != AcceptanceStatus.PENDING
                && confirmer.getAcceptanceStatus() != AcceptanceStatus.EXPIRED) {
            throw new CustomException(ErrorCode.CONFIRMER_RESEND_NOT_ALLOWED);
        }

        confirmer.resetAcceptance();
        issueInviteAndSend(confirmer);

        return ConfirmerResendResponse.of(confirmer, true, null);
    }

    // "확인자 수정하기" - 이름/이메일 수정. 이메일이 바뀌면 재검증이 필요하므로 수락 이메일을 다시 보낸다
    @Transactional
    public ConfirmerUpdateResponse updateConfirmer(UUID userId, UUID planId, UUID confirmId, ConfirmerUpdateRequest request) {
        planOwnershipReader.findOwnedPlan(userId, planId);
        Confirmer confirmer = findOwnedConfirmer(planId, confirmId);

        boolean emailChanged = confirmer.updateContact(request.name(), request.email());
        if (emailChanged) {
            // issue #41 - 이메일 변경은 계정 탈취 대응 시나리오이므로, 이미 발급된 사망신고 토큰도 함께 폐기한다
            securityTokenService.revokeAllForConfirmer(confirmer, SecurityTokenPurpose.REPORT_DEATH);
            issueInviteAndSend(confirmer);
        }

        return ConfirmerUpdateResponse.of(confirmer, emailChanged, null);
    }

    private Confirmer findOwnedConfirmer(UUID planId, UUID confirmId) {
        Confirmer confirmer = confirmerRepository.findById(confirmId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONFIRMER_NOT_FOUND));
        if (!confirmer.getPlan().getPlanId().equals(planId)) {
            throw new CustomException(ErrorCode.CONFIRMER_NOT_FOUND);
        }
        return confirmer;
    }
}
