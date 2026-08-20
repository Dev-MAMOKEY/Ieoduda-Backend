package com.mamoki.ieojuda.domain.account.service;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.audit.entity.EmailLog;
import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.audit.repository.EmailLogRepository;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.entity.DisputeContact;
import com.mamoki.ieojuda.domain.confirmer.entity.Relationship;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.confirmer.repository.DisputeContactRepository;
import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import com.mamoki.ieojuda.domain.evidence.entity.EvidenceDownloadToken;
import com.mamoki.ieojuda.domain.evidence.entity.EvidenceType;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceDownloadTokenRepository;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceRepository;
import com.mamoki.ieojuda.domain.partner.entity.PartnerReviewer;
import com.mamoki.ieojuda.domain.partner.repository.PartnerReviewerRepository;
import com.mamoki.ieojuda.domain.plan.entity.Conversation;
import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import com.mamoki.ieojuda.domain.plan.entity.LifeAreaCategory;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanVersion;
import com.mamoki.ieojuda.domain.plan.repository.ConversationRepository;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanVersionRepository;
import com.mamoki.ieojuda.domain.postaccess.entity.AccessToken;
import com.mamoki.ieojuda.domain.postaccess.entity.PackageActionCompletion;
import com.mamoki.ieojuda.domain.postaccess.repository.AccessTokenRepository;
import com.mamoki.ieojuda.domain.postaccess.repository.PackageActionCompletionRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.entity.RoleType;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import com.mamoki.ieojuda.domain.stage.repository.HandoverStageRepository;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxRepository;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxService;
import com.mamoki.ieojuda.global.storage.EvidenceStorageClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doNothing;

// 버그 회귀 방지 - 계정 탈퇴(UserService.deleteAccount)는 사용 이력이 있는 계정(담당자 초대, 확인자
// 지정, 이의제기 연락처 등록, 사후 인계 발송, 이메일 발송 등)에 대해 항상 DataIntegrityViolationException으로
// 실패했다. email_outbox/posthumouse_access_tokens/package_action_completions/security_tokens/
// evidence_download_tokens가 전부 ON DELETE 지정 없는(NO ACTION) FK로 부모 행을 참조하고 있는데,
// deletePlanData()가 이 자식 행들을 먼저 지우지 않고 부모부터 지우려 했기 때문이다. 목 기반 단위
// 테스트로는 실제 DB 제약을 검증할 수 없어(이 세션에서 반복적으로 겪은 함정과 동일한 종류), 실제
// Postgres에 이 관계를 전부 채운 뒤 실제 deleteAccount()를 호출하는 통합 테스트로만 검증할 수 있다.
@SpringBootTest
class UserAccountDeletionIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlanRepository planRepository;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private LifeAreaRepository lifeAreaRepository;
    @Autowired
    private RecipientRepository recipientRepository;
    @Autowired
    private ConfirmerRepository confirmerRepository;
    @Autowired
    private DisputeContactRepository disputeContactRepository;
    @Autowired
    private PlanVersionRepository planVersionRepository;
    @Autowired
    private ReleaseCaseRepository releaseCaseRepository;
    @Autowired
    private HandoverStageRepository handoverStageRepository;
    @Autowired
    private AccessTokenRepository accessTokenRepository;
    @Autowired
    private PackageActionCompletionRepository packageActionCompletionRepository;
    @Autowired
    private EvidenceRepository evidenceRepository;
    @Autowired
    private EvidenceDownloadTokenRepository evidenceDownloadTokenRepository;
    @Autowired
    private EmailLogRepository emailLogRepository;
    @Autowired
    private EmailOutboxRepository emailOutboxRepository;
    @Autowired
    private EmailOutboxService emailOutboxService;
    @Autowired
    private SecurityTokenService securityTokenService;
    @Autowired
    private PartnerReviewerRepository partnerReviewerRepository;
    @Autowired
    private UserService userService;

    @MockitoBean
    private EvidenceStorageClient evidenceStorageClient;

    @Test
    void deleteAccount_whenPlanHasFullUsageHistory_succeedsWithoutForeignKeyViolation() {
        doNothing().when(evidenceStorageClient).delete(org.mockito.ArgumentMatchers.anyString());

        User user = userRepository.saveAndFlush(User.builder()
                .email("delete-full-" + UUID.randomUUID() + "@test.com").password("hash").name("탈퇴테스트").build());
        Plan plan = planRepository.saveAndFlush(Plan.builder().user(user).build());
        Conversation conversation = conversationRepository.saveAndFlush(Conversation.builder().plan(plan).build());
        LifeArea lifeArea = lifeAreaRepository.saveAndFlush(
                LifeArea.builder().plan(plan).conversation(conversation).category(LifeAreaCategory.RELATIONSHIP_CLEANUP).build());

        // 주 담당자 + 대체 담당자 (role_assignees.backup_for_id 자기참조 - 삭제 순서 검증) 둘 다 토큰 보유
        Recipient primary = recipientRepository.saveAndFlush(Recipient.builder()
                .plan(plan).lifeArea(lifeArea).name("주담당").email("primary-" + UUID.randomUUID() + "@test.com")
                .roleType(RoleType.RELATIONSHIP_MANAGER).isBackup(false)
                .disclosureScope(DisclosureScope.RELATIONSHIP).maxWaitHours(168).backupFor(null).build());
        Recipient backup = recipientRepository.saveAndFlush(Recipient.builder()
                .plan(plan).lifeArea(lifeArea).name("대체담당").email("backup-" + UUID.randomUUID() + "@test.com")
                .roleType(RoleType.RELATIONSHIP_MANAGER).isBackup(true)
                .disclosureScope(DisclosureScope.RELATIONSHIP).maxWaitHours(168).backupFor(primary).build());
        securityTokenService.issueForRecipient(SecurityTokenPurpose.ACCEPT_ROLE, primary, LocalDateTime.now().plusDays(1));
        securityTokenService.issueForRecipient(SecurityTokenPurpose.ACCEPT_ROLE, backup, LocalDateTime.now().plusDays(1));

        // 확인자 + 사건 - security_tokens.confirmer_id / case_id 둘 다 검증
        Confirmer confirmer = confirmerRepository.saveAndFlush(Confirmer.builder()
                .plan(plan).name("확인자").relationship(Relationship.FRIEND).email("confirmer-" + UUID.randomUUID() + "@test.com").build());
        PlanVersion planVersion = planVersionRepository.saveAndFlush(
                PlanVersion.builder().plan(plan).versionNum(1).snapshotData("{}").build());
        ReleaseCase releaseCase = releaseCaseRepository.saveAndFlush(
                ReleaseCase.builder().plan(plan).planVersion(planVersion).build());
        // 삭제 시도 시점에 "진행 중인 사건"으로 간주되지 않도록(ReleaseCaseGuardService) 완료 상태까지 진행시킨다 -
        // 이 테스트의 목적은 그 계정 삭제 가드가 아니라 그 뒤에 남는 자식 행(security_tokens 등) 정리 검증
        releaseCase.confirmReport();
        releaseCase.awaitEvidence();
        releaseCase.startEvidenceReview();
        releaseCase.approveEvidenceAndStartWaiting(0);
        releaseCase.startReleasing();
        releaseCase.complete();
        releaseCase = releaseCaseRepository.saveAndFlush(releaseCase);
        securityTokenService.issueForConfirmer(SecurityTokenPurpose.REPORT_DEATH, confirmer, releaseCase, LocalDateTime.now().plusDays(1));

        // 이의 제기 연락처 - security_tokens.dispute_contact_id
        DisputeContact disputeContact = disputeContactRepository.saveAndFlush(
                DisputeContact.builder().plan(plan).name("이의연락처").email("dispute-" + UUID.randomUUID() + "@test.com").build());
        securityTokenService.issueForDisputeContact(SecurityTokenPurpose.RAISE_OBJECTION, disputeContact, releaseCase, LocalDateTime.now().plusDays(1));

        // 발송된 단계 - posthumouse_access_tokens.stage_id / package_action_completions.stage_id
        HandoverStage stage = handoverStageRepository.saveAndFlush(
                HandoverStage.builder().plan(plan).recipient(primary).stageOrder(0).build());
        stage.send();
        stage = handoverStageRepository.saveAndFlush(stage);
        accessTokenRepository.saveAndFlush(AccessToken.builder()
                .handoverStage(stage).tokenHash("delete-test-token-hash-" + UUID.randomUUID())
                .expiresAt(LocalDateTime.now().plusDays(1)).build());
        packageActionCompletionRepository.saveAndFlush(
                PackageActionCompletion.builder().handoverStage(stage).itemId(UUID.randomUUID()).build());

        // 외부 파트너가 발급받은 증빙 다운로드 토큰 - evidence_download_tokens.evidence_id
        User reviewerUser = userRepository.saveAndFlush(User.builder()
                .email("reviewer-" + UUID.randomUUID() + "@test.com").password("hash").name("외부검토자").build());
        PartnerReviewer reviewer = partnerReviewerRepository.saveAndFlush(
                PartnerReviewer.builder().user(reviewerUser).name("외부검토자").email(reviewerUser.getEmail()).build());
        Evidence evidence = evidenceRepository.saveAndFlush(Evidence.builder()
                .confirmer(confirmer).plan(plan).releaseCase(releaseCase)
                .storageKey("evidence/delete-test/" + UUID.randomUUID()).fileName("proof.pdf").mimeType("application/pdf")
                .integrityHash("hash").evidenceType(EvidenceType.DEATH_CERTIFICATE).build());
        evidenceDownloadTokenRepository.saveAndFlush(EvidenceDownloadToken.builder()
                .tokenHash("download-token-hash-" + UUID.randomUUID()).evidence(evidence).reviewer(reviewer)
                .expiresAt(LocalDateTime.now().plusDays(1)).build());

        // 발송 이력 - email_outbox.log_id -> email_logs
        emailOutboxService.enqueue(plan, null, EmailType.CONFIRMER_ACCEPTANCE_INVITE, confirmer.getEmail(),
                new EmailContent("제목", "본문"));

        UUID userId = user.getUserId();

        assertThatCode(() -> userService.deleteAccount(userId)).doesNotThrowAnyException();

        assertThat(userRepository.findById(userId)).isEmpty();
        assertThat(planRepository.findByUser_UserId(userId)).isEmpty();

        // 이 계정 소유가 아닌 외부 검토자 계정은 건드리지 않아야 한다
        assertThat(userRepository.findById(reviewerUser.getUserId())).isPresent();
        partnerReviewerRepository.delete(reviewer);
        userRepository.delete(reviewerUser);
    }
}
