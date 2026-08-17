package com.mamoki.ieojuda.domain.handoffcheck.service;

import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckAssigneeResponse;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckConfirmerResponse;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckSendRequest;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckSendResponse;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckStatusResponse;
import com.mamoki.ieojuda.domain.handoffcheck.entity.HandoffCheck;
import com.mamoki.ieojuda.domain.handoffcheck.entity.HandoffCheckResponse;
import com.mamoki.ieojuda.domain.handoffcheck.repository.HandoffCheckRepository;
import com.mamoki.ieojuda.domain.handoffcheck.repository.HandoffCheckResponseRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanStatus;
import com.mamoki.ieojuda.domain.plan.service.PlanOwnershipReader;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxService;
import com.mamoki.ieojuda.global.email.template.EmailBuilder;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// "선택형 생전 인계 점검" 화면 - 역할 담당자와 지정 확인자의 준비 상태를 함께 조회하고, 점검 메일을 발송
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HandoffCheckService {

    private final PlanOwnershipReader planOwnershipReader;
    private final RecipientRepository recipientRepository;
    private final ConfirmerRepository confirmerRepository;
    private final HandoffCheckRepository handoffCheckRepository;
    private final HandoffCheckResponseRepository handoffCheckResponseRepository;
    private final EmailOutboxService emailOutboxService;
    private final AppProperties appProperties;

    public HandoffCheckStatusResponse getHandoffCheck(Long userId, Long planId) {
        planOwnershipReader.findOwnedPlan(userId, planId);

        List<HandoffCheckAssigneeResponse> assignees = buildAssignees(planId);
        List<HandoffCheckConfirmerResponse> confirmers = confirmerRepository.findByPlan_PlanIdOrderByConfirmIdAsc(planId).stream()
                .map(HandoffCheckConfirmerResponse::from)
                .toList();

        return HandoffCheckStatusResponse.of(assignees, confirmers);
    }

    // 작성자가 담당자에게 점검 메일을 발송 - 봉인된 계획에서만 가능 (명세서 "선택형 생전 인계 점검" 진입 조건)
    @Transactional
    public HandoffCheckSendResponse sendCheck(Long userId, Long planId, HandoffCheckSendRequest request) {
        Plan plan = planOwnershipReader.findOwnedPlan(userId, planId);
        if (plan.getStatus() != PlanStatus.SEALED) {
            throw new CustomException(ErrorCode.PLAN_NOT_SEALED);
        }

        List<Recipient> targets = resolveTargets(planId, request);
        HandoffCheck handoffCheck = handoffCheckRepository.save(HandoffCheck.builder().plan(plan).build());

        for (Recipient recipient : targets) {
            issueAndSend(handoffCheck, recipient);
        }

        return HandoffCheckSendResponse.of(handoffCheck, targets.size());
    }

    // recipientIds 생략 시 대체 담당자를 제외한 전체 담당자에게 발송 (기존 점검 화면 대상 목록과 동일한 기준)
    private List<Recipient> resolveTargets(Long planId, HandoffCheckSendRequest request) {
        if (request == null || request.recipientIds() == null || request.recipientIds().isEmpty()) {
            return recipientRepository.findByPlan_PlanIdAndIsBackupFalseOrderByAssigneeIdAsc(planId);
        }

        List<Recipient> targets = recipientRepository.findAllById(request.recipientIds());
        if (targets.size() != request.recipientIds().size()) {
            throw new CustomException(ErrorCode.RECIPIENT_NOT_FOUND);
        }
        for (Recipient recipient : targets) {
            if (!recipient.getPlan().getPlanId().equals(planId)) {
                throw new CustomException(ErrorCode.RECIPIENT_NOT_FOUND);
            }
        }
        return targets;
    }

    private void issueAndSend(HandoffCheck handoffCheck, Recipient recipient) {
        HandoffCheckResponse response = handoffCheckResponseRepository.save(
                HandoffCheckResponse.builder().handoffCheck(handoffCheck).recipient(recipient).build());

        String plainToken = TokenProvider.generatePlainToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(appProperties.getInviteTokenTtlHours());
        response.issueInviteToken(TokenProvider.hashToken(plainToken), expiresAt);

        String secureLink = appProperties.getBaseUrl() + "/handoff-checks/" + plainToken;
        EmailContent content = EmailBuilder.build(
                recipient.getRoleType().label(),
                "역할 이해 여부를 확인해 주세요.",
                expiresAt.atZone(ZoneId.systemDefault()),
                secureLink,
                appProperties.getContactEmail()
        );
        emailOutboxService.enqueue(recipient.getPlan(), null, EmailType.HANDOFF_CHECK, recipient.getEmail(), content);
    }

    // 대체 담당자는 별도 박스가 아니라 주 담당자 박스 안에 표시되므로, 대체 담당자를 주 담당자 ID로 매핑해 함께 조회한다 (N+1 방지)
    private List<HandoffCheckAssigneeResponse> buildAssignees(Long planId) {
        List<Recipient> recipients = recipientRepository.findByPlan_PlanId(planId);

        Map<Long, Recipient> backupByPrimaryId = new HashMap<>();
        for (Recipient recipient : recipients) {
            if (Boolean.TRUE.equals(recipient.getIsBackup()) && recipient.getBackupFor() != null) {
                backupByPrimaryId.put(recipient.getBackupFor().getAssigneeId(), recipient);
            }
        }

        // 담당자별 최신 점검 응답 행 - 여러 번 점검을 보냈다면 가장 최근에 발송된 점검 것만 화면에 반영
        Map<Long, HandoffCheckResponse> latestResponseByAssigneeId = new HashMap<>();
        for (HandoffCheckResponse response : handoffCheckResponseRepository.findByHandoffCheck_Plan_PlanId(planId)) {
            Long assigneeId = response.getRecipient().getAssigneeId();
            HandoffCheckResponse current = latestResponseByAssigneeId.get(assigneeId);
            if (current == null || isMoreRecent(response, current)) {
                latestResponseByAssigneeId.put(assigneeId, response);
            }
        }

        return recipients.stream()
                .filter(recipient -> !Boolean.TRUE.equals(recipient.getIsBackup()))
                .sorted(Comparator.comparing(Recipient::getAssigneeId))
                .map(recipient -> HandoffCheckAssigneeResponse.of(
                        recipient,
                        backupByPrimaryId.get(recipient.getAssigneeId()),
                        latestResponseByAssigneeId.get(recipient.getAssigneeId())))
                .toList();
    }

    private boolean isMoreRecent(HandoffCheckResponse candidate, HandoffCheckResponse current) {
        return candidate.getHandoffCheck().getSentAt().isAfter(current.getHandoffCheck().getSentAt());
    }
}
