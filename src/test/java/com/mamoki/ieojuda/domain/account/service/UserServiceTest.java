package com.mamoki.ieojuda.domain.account.service;

import com.mamoki.ieojuda.domain.account.dto.UserUpdateRequest;
import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.RefreshSessionRepository;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.audit.repository.EmailLogRepository;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.entity.DisputeContact;
import com.mamoki.ieojuda.domain.confirmer.entity.Relationship;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.confirmer.repository.DisputeContactRepository;
import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceRepository;
import com.mamoki.ieojuda.domain.partner.repository.PartnerReviewerRepository;
import com.mamoki.ieojuda.domain.handoffcheck.repository.HandoffCheckRepository;
import com.mamoki.ieojuda.domain.handoffcheck.repository.HandoffCheckResponseRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.ConversationRepository;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaMessageRepository;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanVersionRepository;
import com.mamoki.ieojuda.domain.postaccess.repository.PackageIssueRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.domain.releasecase.repository.ObjectionRepository;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.releasecase.service.ReleaseCaseGuardService;
import com.mamoki.ieojuda.domain.stage.repository.DependencyRepository;
import com.mamoki.ieojuda.domain.stage.repository.HandoverStageRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.storage.EvidenceStorageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// issue #48 회귀 테스트 - 계정 이메일 변경 시 토큰 무효화, 계정 삭제 시 진행 중 사건 차단/고아 데이터 방지를 검증한다.
class UserServiceTest {

    private UserRepository userRepository;
    private PlanRepository planRepository;
    private ConversationRepository conversationRepository;
    private LifeAreaRepository lifeAreaRepository;
    private LifeAreaMessageRepository lifeAreaMessageRepository;
    private ItemRepository itemRepository;
    private RecipientRepository recipientRepository;
    private DisputeContactRepository disputeContactRepository;
    private ConfirmerRepository confirmerRepository;
    private ReleaseCaseRepository releaseCaseRepository;
    private PlanVersionRepository planVersionRepository;
    private EvidenceRepository evidenceRepository;
    private EmailLogRepository emailLogRepository;
    private HandoverStageRepository handoverStageRepository;
    private ObjectionRepository objectionRepository;
    private HandoffCheckRepository handoffCheckRepository;
    private HandoffCheckResponseRepository handoffCheckResponseRepository;
    private PackageIssueRepository packageIssueRepository;
    private DependencyRepository dependencyRepository;
    private EvidenceStorageClient evidenceStorageClient;
    private ReleaseCaseGuardService releaseCaseGuardService;
    private SessionRevocationService sessionRevocationService;
    private RefreshSessionRepository refreshSessionRepository;
    private PartnerReviewerRepository partnerReviewerRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        planRepository = mock(PlanRepository.class);
        conversationRepository = mock(ConversationRepository.class);
        lifeAreaRepository = mock(LifeAreaRepository.class);
        lifeAreaMessageRepository = mock(LifeAreaMessageRepository.class);
        itemRepository = mock(ItemRepository.class);
        recipientRepository = mock(RecipientRepository.class);
        disputeContactRepository = mock(DisputeContactRepository.class);
        confirmerRepository = mock(ConfirmerRepository.class);
        releaseCaseRepository = mock(ReleaseCaseRepository.class);
        planVersionRepository = mock(PlanVersionRepository.class);
        evidenceRepository = mock(EvidenceRepository.class);
        emailLogRepository = mock(EmailLogRepository.class);
        handoverStageRepository = mock(HandoverStageRepository.class);
        objectionRepository = mock(ObjectionRepository.class);
        handoffCheckRepository = mock(HandoffCheckRepository.class);
        handoffCheckResponseRepository = mock(HandoffCheckResponseRepository.class);
        packageIssueRepository = mock(PackageIssueRepository.class);
        dependencyRepository = mock(DependencyRepository.class);
        evidenceStorageClient = mock(EvidenceStorageClient.class);
        releaseCaseGuardService = mock(ReleaseCaseGuardService.class);
        sessionRevocationService = mock(SessionRevocationService.class);
        refreshSessionRepository = mock(RefreshSessionRepository.class);
        partnerReviewerRepository = mock(PartnerReviewerRepository.class);

        userService = new UserService(
                userRepository, planRepository, conversationRepository, lifeAreaRepository, lifeAreaMessageRepository,
                itemRepository, recipientRepository, disputeContactRepository, confirmerRepository,
                releaseCaseRepository, planVersionRepository, evidenceRepository, emailLogRepository,
                handoverStageRepository, objectionRepository, handoffCheckRepository, handoffCheckResponseRepository,
                packageIssueRepository, dependencyRepository, evidenceStorageClient, releaseCaseGuardService,
                sessionRevocationService, refreshSessionRepository, partnerReviewerRepository);
    }

    @Test
    void updateProfile_whenEmailChanges_invalidatesEveryPlanInviteToken() {
        User user = User.builder().email("old@test.com").password("hash").name("A").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndUserIdNot("new@test.com", 1L)).thenReturn(false);

        Plan plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(10L);
        when(planRepository.findByUser_UserId(1L)).thenReturn(Optional.of(plan));

        Recipient recipient = mock(Recipient.class);
        when(recipientRepository.findByPlan_PlanId(10L)).thenReturn(List.of(recipient));

        Confirmer confirmer = Confirmer.builder().plan(plan).name("B").relationship(Relationship.FRIEND).email("b@test.com").build();
        confirmer.issueInviteToken("some-hash", null);
        when(confirmerRepository.findByPlan_PlanIdOrderByConfirmIdAsc(10L)).thenReturn(List.of(confirmer));

        DisputeContact disputeContact = mock(DisputeContact.class);
        when(disputeContactRepository.findByPlan_PlanId(10L)).thenReturn(List.of(disputeContact));

        userService.updateProfile(1L, new UserUpdateRequest("new@test.com", "A"));

        assertThat(user.getEmail()).isEqualTo("new@test.com");
        verify(recipient).invalidateInviteToken();
        assertThat(confirmer.getInviteToken()).isNull();
        verify(disputeContact).invalidateInviteToken();
        verify(sessionRevocationService).revokeAllSessions(user);
    }

    @Test
    void updateProfile_whenEmailUnchanged_doesNotTouchInviteTokens() {
        User user = User.builder().email("same@test.com").password("hash").name("A").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.updateProfile(1L, new UserUpdateRequest("same@test.com", "New Name"));

        assertThat(user.getName()).isEqualTo("New Name");
        verifyNoInteractions(planRepository, recipientRepository, confirmerRepository, disputeContactRepository,
                sessionRevocationService);
    }

    @Test
    void deleteAccount_whenActiveCaseExists_isBlockedAndDeletesNothing() {
        User user = User.builder().email("owner@test.com").password("hash").name("A").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Plan plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(10L);
        when(planRepository.findByUser_UserId(1L)).thenReturn(Optional.of(plan));
        when(releaseCaseGuardService.freezeIfActiveCaseExists(10L)).thenReturn(true);

        assertThatThrownBy(() -> userService.deleteAccount(1L))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACTIVE_RELEASE_CASE_EXISTS));

        verify(userRepository, never()).delete(user);
        verifyNoInteractions(itemRepository, recipientRepository, releaseCaseRepository, evidenceStorageClient);
    }

    @Test
    void deleteAccount_whenNoActiveCase_deletesPlanDataAndEvidenceStorageThenUser() {
        User user = User.builder().email("owner@test.com").password("hash").name("A").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Plan plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(10L);
        when(planRepository.findByUser_UserId(1L)).thenReturn(Optional.of(plan));
        when(releaseCaseGuardService.freezeIfActiveCaseExists(10L)).thenReturn(false);

        Evidence undeletedEvidence = mock(Evidence.class);
        when(undeletedEvidence.getDeletedAt()).thenReturn(null);
        when(undeletedEvidence.getStorageKey()).thenReturn("evidence/10/real-file.pdf");

        Evidence alreadyDeletedEvidence = mock(Evidence.class);
        when(alreadyDeletedEvidence.getDeletedAt()).thenReturn(java.time.LocalDateTime.now());

        when(evidenceRepository.findByPlan_PlanId(10L)).thenReturn(List.of(undeletedEvidence, alreadyDeletedEvidence));

        userService.deleteAccount(1L);

        // S3 원본은 아직 안 지워진 증빙만 골라서 지운다
        verify(evidenceStorageClient).delete("evidence/10/real-file.pdf");
        verify(evidenceStorageClient, org.mockito.Mockito.times(1)).delete(org.mockito.ArgumentMatchers.anyString());

        verify(userRepository).delete(user);
    }

    // #56이 도입한 RefreshSession/PartnerReviewer는 Plan이 아니라 User에 직접 딸린 행이라
    // deletePlanData()의 Plan 하위 삭제만으로는 안 지워진다 - 지우지 않고 유저부터 지우면
    // refresh_sessions.user_id FK 위반으로 실패한다(실제 운영 DB에서 재현됨).
    @Test
    void deleteAccount_deletesRefreshSessionsAndPartnerReviewerBeforeDeletingUser() {
        User user = User.builder().email("owner@test.com").password("hash").name("A").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(planRepository.findByUser_UserId(1L)).thenReturn(Optional.empty());

        com.mamoki.ieojuda.domain.account.entity.RefreshSession session =
                mock(com.mamoki.ieojuda.domain.account.entity.RefreshSession.class);
        when(refreshSessionRepository.findByUser_UserId(1L)).thenReturn(List.of(session));

        com.mamoki.ieojuda.domain.partner.entity.PartnerReviewer reviewer =
                mock(com.mamoki.ieojuda.domain.partner.entity.PartnerReviewer.class);
        when(partnerReviewerRepository.findByUser_UserId(1L)).thenReturn(Optional.of(reviewer));

        userService.deleteAccount(1L);

        verify(refreshSessionRepository).deleteAll(List.of(session));
        verify(partnerReviewerRepository).delete(reviewer);
        verify(userRepository).delete(user);
    }
}
