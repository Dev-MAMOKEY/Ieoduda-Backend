package com.mamoki.ieojuda.domain.handoffcheck.service;

import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckAssigneeResponse;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckSendRequest;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckSendResponse;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckStatusResponse;
import com.mamoki.ieojuda.domain.handoffcheck.entity.HandoffCheck;
import com.mamoki.ieojuda.domain.handoffcheck.entity.HandoffCheckResponse;
import com.mamoki.ieojuda.domain.handoffcheck.repository.HandoffCheckRepository;
import com.mamoki.ieojuda.domain.handoffcheck.repository.HandoffCheckResponseRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanStatus;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.domain.plan.service.PlanOwnershipReader;
import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.entity.RoleType;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxService;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HandoffCheckServiceTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID ATTACKER_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();

    private PlanRepository planRepository;
    private RecipientRepository recipientRepository;
    private ConfirmerRepository confirmerRepository;
    private HandoffCheckRepository handoffCheckRepository;
    private HandoffCheckResponseRepository handoffCheckResponseRepository;
    private EmailOutboxService emailOutboxService;
    private AppProperties appProperties;
    private HandoffCheckService handoffCheckService;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        recipientRepository = mock(RecipientRepository.class);
        confirmerRepository = mock(ConfirmerRepository.class);
        handoffCheckRepository = mock(HandoffCheckRepository.class);
        handoffCheckResponseRepository = mock(HandoffCheckResponseRepository.class);
        emailOutboxService = mock(EmailOutboxService.class);
        appProperties = mock(AppProperties.class);
        when(appProperties.getInviteTokenTtlHours()).thenReturn(24L);
        when(appProperties.getBaseUrl()).thenReturn("https://ieoduda.app");
        when(appProperties.getContactEmail()).thenReturn("help@ieoduda.app");

        handoffCheckService = new HandoffCheckService(
                new PlanOwnershipReader(planRepository),
                recipientRepository,
                confirmerRepository,
                handoffCheckRepository,
                handoffCheckResponseRepository,
                emailOutboxService,
                appProperties);

        when(handoffCheckRepository.save(any(HandoffCheck.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(handoffCheckResponseRepository.save(any(HandoffCheckResponse.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Plan sealedPlan() {
        Plan plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(PLAN_ID);
        when(plan.getStatus()).thenReturn(PlanStatus.SEALED);
        return plan;
    }

    private Recipient recipient(UUID assigneeId, Plan plan, RoleType roleType) {
        Recipient recipient = mock(Recipient.class);
        when(recipient.getAssigneeId()).thenReturn(assigneeId);
        when(recipient.getPlan()).thenReturn(plan);
        when(recipient.getEmail()).thenReturn("recipient-" + assigneeId + "@test.com");
        when(recipient.getRoleType()).thenReturn(roleType);
        return recipient;
    }

    // BOLA - 다른 사용자의 계획에 점검을 발송하려 하면 PLAN_NOT_FOUND로 막혀야 하고, 어떤 부수효과도 없어야 한다.
    @Test
    void sendCheck_rejectsNonOwnerAndDoesNotSendAnything() {
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, ATTACKER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handoffCheckService.sendCheck(ATTACKER_ID, PLAN_ID, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        verifyNoInteractions(handoffCheckRepository, handoffCheckResponseRepository, emailOutboxService);
    }

    @Test
    void sendCheck_throwsPlanNotSealed_whenPlanIsDraft() {
        Plan draftPlan = mock(Plan.class);
        when(draftPlan.getPlanId()).thenReturn(PLAN_ID);
        when(draftPlan.getStatus()).thenReturn(PlanStatus.DRAFT);
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, OWNER_ID)).thenReturn(Optional.of(draftPlan));

        assertThatThrownBy(() -> handoffCheckService.sendCheck(OWNER_ID, PLAN_ID, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_SEALED));
        verifyNoInteractions(handoffCheckRepository, handoffCheckResponseRepository, emailOutboxService);
    }

    @Test
    void sendCheck_sendsToAllNonBackupRecipients_whenRecipientIdsOmitted() {
        Plan plan = sealedPlan();
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, OWNER_ID)).thenReturn(Optional.of(plan));
        Recipient r1 = recipient(UUID.randomUUID(), plan, RoleType.FAMILY_MANAGER);
        Recipient r2 = recipient(UUID.randomUUID(), plan, RoleType.WORK_MANAGER);
        when(recipientRepository.findByPlan_PlanIdAndIsBackupFalseOrderByAssigneeIdAsc(PLAN_ID)).thenReturn(List.of(r1, r2));

        HandoffCheckSendResponse result = handoffCheckService.sendCheck(OWNER_ID, PLAN_ID, null);

        assertThat(result.targetCount()).isEqualTo(2);
        verify(handoffCheckRepository, times(1)).save(any(HandoffCheck.class));
        verify(handoffCheckResponseRepository, times(2)).save(any(HandoffCheckResponse.class));
        verify(emailOutboxService, times(2))
                .enqueue(eq(plan), isNull(), eq(EmailType.HANDOFF_CHECK), anyString(), any(EmailContent.class));
    }

    @Test
    void sendCheck_sendsOnlyToRequestedRecipients_whenRecipientIdsProvided() {
        Plan plan = sealedPlan();
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, OWNER_ID)).thenReturn(Optional.of(plan));
        UUID recipientId = UUID.randomUUID();
        Recipient r1 = recipient(recipientId, plan, RoleType.FAMILY_MANAGER);
        when(recipientRepository.findAllById(List.of(recipientId))).thenReturn(List.of(r1));

        HandoffCheckSendResponse result = handoffCheckService.sendCheck(OWNER_ID, PLAN_ID, new HandoffCheckSendRequest(List.of(recipientId)));

        assertThat(result.targetCount()).isEqualTo(1);
        verify(emailOutboxService, times(1))
                .enqueue(eq(plan), isNull(), eq(EmailType.HANDOFF_CHECK), eq("recipient-" + recipientId + "@test.com"), any(EmailContent.class));
    }

    @Test
    void sendCheck_throwsRecipientNotFound_whenRequestedRecipientBelongsToAnotherPlan() {
        Plan plan = sealedPlan();
        Plan otherPlan = mock(Plan.class);
        when(otherPlan.getPlanId()).thenReturn(UUID.randomUUID());
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, OWNER_ID)).thenReturn(Optional.of(plan));
        UUID foreignId = UUID.randomUUID();
        Recipient foreign = recipient(foreignId, otherPlan, RoleType.FAMILY_MANAGER);
        when(recipientRepository.findAllById(List.of(foreignId))).thenReturn(List.of(foreign));

        assertThatThrownBy(() -> handoffCheckService.sendCheck(OWNER_ID, PLAN_ID, new HandoffCheckSendRequest(List.of(foreignId))))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RECIPIENT_NOT_FOUND));
        verifyNoInteractions(handoffCheckRepository, emailOutboxService);
    }

    @Test
    void sendCheck_throwsRecipientNotFound_whenSomeRequestedIdsDoNotExist() {
        Plan plan = sealedPlan();
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, OWNER_ID)).thenReturn(Optional.of(plan));
        UUID existingId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        Recipient r1 = recipient(existingId, plan, RoleType.FAMILY_MANAGER);
        // 두 ID를 요청했지만 missingId는 존재하지 않아 한 건만 조회됨
        when(recipientRepository.findAllById(List.of(existingId, missingId))).thenReturn(List.of(r1));

        assertThatThrownBy(() -> handoffCheckService.sendCheck(OWNER_ID, PLAN_ID, new HandoffCheckSendRequest(List.of(existingId, missingId))))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RECIPIENT_NOT_FOUND));
        verifyNoInteractions(handoffCheckRepository, emailOutboxService);
    }

    // GET 갱신 - 실제 점검 발송·응답 이력이 새 필드에 반영되어야 한다
    @Test
    void getHandoffCheck_reflectsLatestCheckResponseHistory() {
        Plan plan = mock(Plan.class);
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, OWNER_ID)).thenReturn(Optional.of(plan));

        Recipient r1 = mock(Recipient.class);
        when(r1.getAssigneeId()).thenReturn(UUID.randomUUID());
        when(r1.getName()).thenReturn("A");
        when(r1.getRoleType()).thenReturn(RoleType.FAMILY_MANAGER);
        when(r1.getIsBackup()).thenReturn(false);
        when(r1.getBackupFor()).thenReturn(null);
        when(r1.getAcceptanceStatus()).thenReturn(AcceptanceStatus.ACCEPTED);
        when(r1.getInviteSent()).thenReturn(true);
        when(r1.getInquiry()).thenReturn(null);
        when(recipientRepository.findByPlan_PlanId(PLAN_ID)).thenReturn(List.of(r1));
        when(confirmerRepository.findByPlan_PlanIdOrderByConfirmIdAsc(PLAN_ID)).thenReturn(List.of());

        LocalDateTime sentAt = LocalDateTime.of(2026, 8, 17, 10, 0);
        LocalDateTime respondedAt = LocalDateTime.of(2026, 8, 17, 12, 0);
        HandoffCheck check = mock(HandoffCheck.class);
        when(check.getSentAt()).thenReturn(sentAt);
        HandoffCheckResponse response = mock(HandoffCheckResponse.class);
        when(response.getRecipient()).thenReturn(r1);
        when(response.getHandoffCheck()).thenReturn(check);
        when(response.getEmailReached()).thenReturn(true);
        when(response.getRoleUnderstood()).thenReturn(true);
        when(response.getDisclosureUnderstood()).thenReturn(false);
        when(response.getInquiry()).thenReturn("문의 있어요");
        when(response.getRespondedAt()).thenReturn(respondedAt);
        when(handoffCheckResponseRepository.findByHandoffCheck_Plan_PlanId(PLAN_ID)).thenReturn(List.of(response));

        HandoffCheckStatusResponse result = handoffCheckService.getHandoffCheck(OWNER_ID, PLAN_ID);

        HandoffCheckAssigneeResponse assignee = result.assignees().get(0);
        assertThat(assignee.lastCheckSentAt()).isEqualTo(sentAt);
        assertThat(assignee.checkEmailReached()).isTrue();
        assertThat(assignee.checkRoleUnderstood()).isTrue();
        assertThat(assignee.checkScopeUnderstood()).isFalse();
        assertThat(assignee.checkInquiry()).isEqualTo("문의 있어요");
        assertThat(assignee.checkRespondedAt()).isEqualTo(respondedAt);
    }

    @Test
    void getHandoffCheck_newFieldsAreNull_whenNoCheckEverSent() {
        Plan plan = mock(Plan.class);
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, OWNER_ID)).thenReturn(Optional.of(plan));

        Recipient r1 = mock(Recipient.class);
        when(r1.getAssigneeId()).thenReturn(UUID.randomUUID());
        when(r1.getName()).thenReturn("A");
        when(r1.getRoleType()).thenReturn(RoleType.FAMILY_MANAGER);
        when(r1.getIsBackup()).thenReturn(false);
        when(r1.getAcceptanceStatus()).thenReturn(AcceptanceStatus.PENDING);
        when(recipientRepository.findByPlan_PlanId(PLAN_ID)).thenReturn(List.of(r1));
        when(confirmerRepository.findByPlan_PlanIdOrderByConfirmIdAsc(PLAN_ID)).thenReturn(List.of());
        when(handoffCheckResponseRepository.findByHandoffCheck_Plan_PlanId(PLAN_ID)).thenReturn(List.of());

        HandoffCheckAssigneeResponse assignee = handoffCheckService.getHandoffCheck(OWNER_ID, PLAN_ID).assignees().get(0);

        assertThat(assignee.lastCheckSentAt()).isNull();
        assertThat(assignee.checkEmailReached()).isNull();
        assertThat(assignee.checkRoleUnderstood()).isNull();
        assertThat(assignee.checkScopeUnderstood()).isNull();
        assertThat(assignee.checkInquiry()).isNull();
        assertThat(assignee.checkRespondedAt()).isNull();
    }
}
