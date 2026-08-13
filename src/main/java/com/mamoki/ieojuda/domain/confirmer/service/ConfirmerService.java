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
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.contract.EmailSendResult;
import com.mamoki.ieojuda.global.email.sender.EmailSender;
import com.mamoki.ieojuda.global.email.template.EmailBuilder;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// "지정 확인자 등록" 화면 - 확인자 이름/이메일을 한 번에 등록하고, 등록과 동시에 수락 이메일을 발송
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConfirmerService {

    private final PlanRepository planRepository;
    private final ConfirmerRepository confirmerRepository;
    private final EmailSender emailSender;
    private final AppProperties appProperties;

    @Transactional
    // 특정 계획에 여러 명의 확인자를 한번에 등록하는 함수
    public ConfirmerBulkRegisterResponse registerAll(Long userId, Long planId, ConfirmerBulkRegisterRequest request) {
        Plan plan = findOwnedPlan(userId, planId);
        validateAll(planId, request.confirmers());

        List<ConfirmerRegisterResponse> responses = new ArrayList<>();
        for (ConfirmerRegisterRequest confirmerRequest : request.confirmers()) {
            responses.add(registerOne(plan, confirmerRequest));
        }

        return new ConfirmerBulkRegisterResponse(responses);
    }

    // 이메일은 발송 후 되돌릴 수 없으므로, 저장·발송을 시작하기 전에 요청 전체를 먼저 검증한다
    private void validateAll(Long planId, List<ConfirmerRegisterRequest> requests) {
        Set<String> requestedEmails = new HashSet<>();

        for (ConfirmerRegisterRequest request : requests) {
            // 같은 사람이 두 번 등록되면 "독립된 2인 확인" 원칙이 무력화되므로 이메일 중복을 차단
            if (!requestedEmails.add(request.email())
                    || confirmerRepository.existsByPlan_PlanIdAndEmail(planId, request.email())) {
                throw new CustomException(ErrorCode.CONFIRMER_ALREADY_REGISTERED);
            }
        }
    }

    // 확인자 저장 + 초대 토큰 발급 + 수락 이메일 발송
    // 이메일 발송 실패는 예외로 던지지 않는다 - 확인자 저장은 유지하고 건별 결과만 응답에 담는다
    private ConfirmerRegisterResponse registerOne(Plan plan, ConfirmerRegisterRequest request) {
        Confirmer confirmer = confirmerRepository.save(Confirmer.builder()
                .plan(plan)
                .name(request.name())
                .email(request.email())
                .build());

        EmailSendResult sendResult = issueInviteAndSend(confirmer);

        return ConfirmerRegisterResponse.of(
                confirmer,
                sendResult.success(),
                sendResult.bounceType() == null ? null : sendResult.bounceType().name()
        );
    }

    private EmailSendResult issueInviteAndSend(Confirmer confirmer) {
        String plainToken = TokenProvider.generatePlainToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(appProperties.getInviteTokenTtlHours());
        confirmer.issueInviteToken(TokenProvider.hashToken(plainToken), expiresAt);
        return sendAcceptanceEmail(confirmer, plainToken, expiresAt);
    }

    private EmailSendResult sendAcceptanceEmail(Confirmer confirmer, String plainToken, LocalDateTime expiresAt) {
        String secureLink = appProperties.getBaseUrl() + "/confirmer-acceptances/" + plainToken;
        EmailContent content = EmailBuilder.build(
                "지정 확인자",
                "지정 확인자 역할 수락 여부를 확인해 주세요.",
                expiresAt.atZone(ZoneId.systemDefault()),
                secureLink,
                appProperties.getContactEmail()
        );
        return emailSender.send(confirmer.getEmail(), content);
    }

    // "역할 점검" 화면 상세 - 이름 클릭 시 해당 확인자 정보
    public ConfirmerDetailResponse getConfirmer(Long userId, Long planId, Long confirmId) {
        findOwnedPlan(userId, planId);
        Confirmer confirmer = findOwnedConfirmer(planId, confirmId);
        return ConfirmerDetailResponse.from(confirmer);
    }

    // "수락 요청 다시 보내기" - 이미 수락/거절한 확인자에게는 재전송하지 않는다
    @Transactional
    public ConfirmerResendResponse resendAcceptanceEmail(Long userId, Long confirmId) {
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
        EmailSendResult sendResult = issueInviteAndSend(confirmer);

        return ConfirmerResendResponse.of(
                confirmer,
                sendResult.success(),
                sendResult.bounceType() == null ? null : sendResult.bounceType().name()
        );
    }

    // "확인자 수정하기" - 이름/이메일 수정. 이메일이 바뀌면 재검증이 필요하므로 수락 이메일을 다시 보낸다
    @Transactional
    public ConfirmerUpdateResponse updateConfirmer(Long userId, Long planId, Long confirmId, ConfirmerUpdateRequest request) {
        findOwnedPlan(userId, planId);
        Confirmer confirmer = findOwnedConfirmer(planId, confirmId);

        boolean emailChanged = confirmer.updateContact(request.name(), request.email());

        EmailSendResult sendResult = emailChanged
                ? issueInviteAndSend(confirmer)
                : null;

        return ConfirmerUpdateResponse.of(
                confirmer,
                sendResult != null && sendResult.success(),
                sendResult == null || sendResult.bounceType() == null ? null : sendResult.bounceType().name()
        );
    }

    // 로그인한 사용자가 자신의 계획에만 확인자를 등록할 수 있도록 검증 (불일치 시 존재 노출 방지를 위해 NOT_FOUND로 응답)
    private Plan findOwnedPlan(Long userId, Long planId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAN_NOT_FOUND));
        if (!plan.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.PLAN_NOT_FOUND);
        }
        return plan;
    }

    private Confirmer findOwnedConfirmer(Long planId, Long confirmId) {
        Confirmer confirmer = confirmerRepository.findById(confirmId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONFIRMER_NOT_FOUND));
        if (!confirmer.getPlan().getPlanId().equals(planId)) {
            throw new CustomException(ErrorCode.CONFIRMER_NOT_FOUND);
        }
        return confirmer;
    }
}
