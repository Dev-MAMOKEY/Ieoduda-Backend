package com.mamoki.ieojuda.domain.confirmer.service;

import com.mamoki.ieojuda.domain.account.entity.AdminPermission;
import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.entity.UserRole;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.audit.entity.EmailLog;
import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.audit.repository.EmailLogRepository;
import com.mamoki.ieojuda.domain.confirmer.dto.DeathReportRequest;
import com.mamoki.ieojuda.domain.confirmer.dto.DeathReportResponse;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.entity.DisputeContact;
import com.mamoki.ieojuda.domain.confirmer.entity.ReportStatus;
import com.mamoki.ieojuda.domain.confirmer.entity.Relationship;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.confirmer.repository.DisputeContactRepository;
import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import com.mamoki.ieojuda.domain.evidence.entity.EvidenceType;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceRepository;
import com.mamoki.ieojuda.domain.evidence.service.EvidenceSubmitService;
import com.mamoki.ieojuda.domain.partner.dto.PartnerReviewDecisionRequest;
import com.mamoki.ieojuda.domain.partner.entity.PartnerReviewer;
import com.mamoki.ieojuda.domain.partner.repository.PartnerReviewerRepository;
import com.mamoki.ieojuda.domain.partner.service.PartnerReviewService;
import com.mamoki.ieojuda.domain.plan.entity.Conversation;
import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.plan.entity.ItemActionType;
import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import com.mamoki.ieojuda.domain.plan.entity.LifeAreaCategory;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.ConversationRepository;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanVersionRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.entity.RoleType;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCaseStatus;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.repository.SecurityTokenRepository;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.email.contract.EmailSendResult;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxRepository;
import com.mamoki.ieojuda.global.email.sender.EmailSender;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.storage.EvidenceStorageClient;
import com.mamoki.ieojuda.global.storage.contract.StoredEvidence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

// issue #41 재설계 - "역할 수락 -> 사망 신고 -> 증빙 제출 -> 매칭 판정 -> 파트너 승인 -> 대기" 전체 흐름을
// 실제 스프링 컨텍스트·DB·토큰 발급/검증으로 태워 검증한다. 외부 경계(S3, SMTP)만 격리한다.
@SpringBootTest
class DeathReportToEvidenceFlowIntegrationTest {

    private static final byte[] PDF_BYTES = "%PDF-1.4\n1 0 obj << >> endobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);

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
    private ItemRepository itemRepository;
    @Autowired
    private ConfirmerRepository confirmerRepository;
    @Autowired
    private DisputeContactRepository disputeContactRepository;
    @Autowired
    private ReleaseCaseRepository releaseCaseRepository;
    @Autowired
    private PlanVersionRepository planVersionRepository;
    @Autowired
    private SecurityTokenRepository securityTokenRepository;
    @Autowired
    private SecurityTokenService securityTokenService;
    @Autowired
    private EvidenceRepository evidenceRepository;
    @Autowired
    private EmailLogRepository emailLogRepository;
    @Autowired
    private EmailOutboxRepository emailOutboxRepository;
    @Autowired
    private PartnerReviewerRepository partnerReviewerRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private DeathReportService deathReportService;
    @Autowired
    private EvidenceSubmitService evidenceSubmitService;
    @Autowired
    private PartnerReviewService partnerReviewService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private EvidenceStorageClient evidenceStorageClient;
    @MockitoBean
    private EmailSender emailSender;

    private User user;
    private Plan plan;
    private Conversation conversation;
    private LifeArea lifeArea;
    private Recipient recipient;
    private Item item;
    private Confirmer confirmerA;
    private Confirmer confirmerB;
    private DisputeContact disputeContact;
    private User reviewerUser;

    @BeforeEach
    void setUp() {
        when(evidenceStorageClient.store(any(), any()))
                .thenAnswer(invocation -> new StoredEvidence("evidence/flow-test/" + UUID.randomUUID() + ".pdf", PDF_BYTES.length));
        // 이의 연락처 경고 발송은 항상 성공한다고 가정 - "매칭/불일치 모두 승인되면 WAITING까지 도달하는가"가
        // 이 테스트의 관심사이지, 경고 메일 발송 자체의 성공/실패 분기는 PartnerReviewWarningIntegrationTest가 이미 다룬다.
        when(emailSender.send(any(), any())).thenReturn(EmailSendResult.success("msg-1"));
    }

    // 이의 연락처 인증·대기 기간·본인 경고 이메일 인증·실행 순서 확정·수락 확인자 2명까지 전부 충족한
    // 계획을 만든다 (DeathReportReadinessIntegrationTest.createFixture와 동일한 패턴).
    private void createReadyFixture() {
        user = userRepository.saveAndFlush(User.builder()
                .email("flow-" + UUID.randomUUID() + "@test.com").password("hash").name("김나무").build());
        plan = planRepository.saveAndFlush(Plan.builder().user(user).build());
        conversation = conversationRepository.saveAndFlush(Conversation.builder().plan(plan).build());
        lifeArea = lifeAreaRepository.saveAndFlush(
                LifeArea.builder().plan(plan).conversation(conversation).category(LifeAreaCategory.RELATIONSHIP_CLEANUP).build());
        recipient = recipientRepository.saveAndFlush(Recipient.builder()
                .plan(plan).lifeArea(lifeArea).name("이지수").email("recipient-" + UUID.randomUUID() + "@test.com")
                .roleType(RoleType.RELATIONSHIP_MANAGER).isBackup(false)
                .disclosureScope(DisclosureScope.RELATIONSHIP).maxWaitHours(168).backupFor(null).build());
        item = Item.builder()
                .lifeArea(lifeArea).targetName("이지수").locationType("인스타그램").action("정리해줘")
                .title("SNS 정리").content("비공개로 전환").precondition("")
                .disclosureScope(DisclosureScope.RELATIONSHIP).sourceExcerpt("근거 발췌")
                .sortOrder(0).actionType(ItemActionType.DELETE).build();
        item.assignRecipient(recipient);
        item = itemRepository.saveAndFlush(item);

        confirmerA = Confirmer.builder().plan(plan).name("확인자A").relationship(Relationship.FRIEND)
                .email("confirmer-a-" + UUID.randomUUID() + "@test.com").build();
        confirmerA.accept(null);
        confirmerA = confirmerRepository.saveAndFlush(confirmerA);
        confirmerB = Confirmer.builder().plan(plan).name("확인자B").relationship(Relationship.FRIEND)
                .email("confirmer-b-" + UUID.randomUUID() + "@test.com").build();
        confirmerB.accept(null);
        confirmerB = confirmerRepository.saveAndFlush(confirmerB);

        disputeContact = DisputeContact.builder().plan(plan)
                .email("dispute-" + UUID.randomUUID() + "@test.com").name("이의연락처").build();
        disputeContact.verify();
        disputeContact = disputeContactRepository.saveAndFlush(disputeContact);

        plan.updateWaitingDays(7);
        plan.requestSelfWarningEmailVerification("warn-" + UUID.randomUUID() + "@test.com", "hash", LocalDateTime.now().plusDays(1));
        plan.verifySelfWarningEmail();
        plan.confirmOrder();
        plan.seal();
        plan = planRepository.saveAndFlush(plan);
    }

    private void createReviewer() {
        reviewerUser = User.builder().email("reviewer-" + UUID.randomUUID() + "@test.com")
                .password(passwordEncoder.encode("correct-pw")).name("검토자").build();
        ReflectionTestUtils.setField(reviewerUser, "role", UserRole.EXTERNAL);
        ReflectionTestUtils.setField(reviewerUser, "permissions", Set.of(AdminPermission.EVIDENCE_REVIEW));
        reviewerUser = userRepository.saveAndFlush(reviewerUser);
        partnerReviewerRepository.saveAndFlush(PartnerReviewer.builder()
                .user(reviewerUser).name("검토자").email(reviewerUser.getEmail()).build());
    }

    @AfterEach
    void tearDown() {
        if (plan == null) {
            return;
        }
        evidenceRepository.findByPlan_PlanId(plan.getPlanId()).forEach(evidenceRepository::delete);
        releaseCaseRepository.findByPlan_PlanId(plan.getPlanId()).forEach(releaseCase -> {
            new TransactionTemplate(transactionManager).executeWithoutResult(
                    status -> securityTokenRepository.deleteByReleaseCase_CaseId(releaseCase.getCaseId()));
            releaseCaseRepository.delete(releaseCase);
        });
        planVersionRepository.findByPlan_PlanId(plan.getPlanId()).forEach(planVersionRepository::delete);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            securityTokenRepository.deleteByConfirmer_ConfirmId(confirmerA.getConfirmId());
            securityTokenRepository.deleteByConfirmer_ConfirmId(confirmerB.getConfirmId());
        });
        confirmerRepository.delete(confirmerA);
        confirmerRepository.delete(confirmerB);
        if (disputeContact != null) {
            disputeContactRepository.delete(disputeContact);
        }
        for (EmailLog emailLog : emailLogRepository.findByPlan_PlanIdOrderByRequestedAtDesc(plan.getPlanId())) {
            new TransactionTemplate(transactionManager).executeWithoutResult(
                    status -> emailOutboxRepository.deleteByEmailLog_LogId(emailLog.getLogId()));
            emailLogRepository.delete(emailLog);
        }
        itemRepository.delete(item);
        recipientRepository.delete(recipient);
        lifeAreaRepository.delete(lifeArea);
        conversationRepository.delete(conversation);
        planRepository.delete(plan);
        userRepository.delete(user);
        if (reviewerUser != null) {
            partnerReviewerRepository.findByUser_UserId(reviewerUser.getUserId())
                    .ifPresent(partnerReviewerRepository::delete);
            userRepository.delete(reviewerUser);
        }
        plan = null;
    }

    private DeathReportResponse report(Confirmer confirmer, LocalDate deathDate) {
        String token = securityTokenService.issueForConfirmer(
                SecurityTokenPurpose.REPORT_DEATH, confirmer, null, LocalDateTime.now().plusDays(1));
        return deathReportService.report(token, new DeathReportRequest(deathDate), UUID.randomUUID().toString());
    }

    private Evidence submitEvidence(UUID caseId, String evidenceUploadToken, String fileName) {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "application/pdf", PDF_BYTES);
        evidenceSubmitService.submit(caseId, evidenceUploadToken, file, EvidenceType.DEATH_CERTIFICATE, UUID.randomUUID().toString());
        return evidenceRepository.findByPlan_PlanId(plan.getPlanId()).stream()
                .filter(e -> e.getStorageKey() != null)
                .reduce((first, second) -> second) // 방금 저장된 것
                .orElseThrow();
    }

    private void approve(Evidence evidence) {
        partnerReviewService.decide(evidence.getEvidenceId(), reviewerUser.getUserId(),
                new PartnerReviewDecisionRequest(PartnerReviewDecisionRequest.PartnerReviewDecision.APPROVE, null, "correct-pw"),
                UUID.randomUUID().toString());
    }

    private boolean hasEvidenceSubmissionRequestEmailFor(String recipientEmail) {
        return emailLogRepository.findByPlan_PlanIdOrderByRequestedAtDesc(plan.getPlanId()).stream()
                .anyMatch(log -> log.getEmailType() == EmailType.EVIDENCE_SUBMISSION_REQUEST
                        && log.getRecipientEmail().equals(recipientEmail));
    }

    // ===================== 성공 흐름 =====================

    @Test
    void success_reportImmediatelyReturnsUsableEvidenceTokenWithoutWaitingForEmail() {
        createReadyFixture();

        DeathReportResponse response = report(confirmerA, LocalDate.of(2026, 8, 15));

        // 응답에 담긴 토큰만으로, 이메일을 기다리지 않고 바로 증빙 제출 화면(API)으로 넘어갈 수 있어야 한다
        assertThat(response.caseId()).isNotNull();
        assertThat(response.evidenceUploadToken()).isNotBlank();
        Evidence evidence = submitEvidence(response.caseId(), response.evidenceUploadToken(), "proof-a.pdf");
        assertThat(evidence.getConfirmer().getConfirmId()).isEqualTo(confirmerA.getConfirmId());

        // 백업 이메일도 함께 발송(큐잉)됐어야 한다
        assertThat(hasEvidenceSubmissionRequestEmailFor(confirmerA.getEmail())).isTrue();
    }

    @Test
    void success_secondConfirmerReport_reusesSameActiveCaseInsteadOfCreatingNew() {
        createReadyFixture();

        DeathReportResponse responseA = report(confirmerA, LocalDate.of(2026, 8, 15));
        DeathReportResponse responseB = report(confirmerB, LocalDate.of(2026, 8, 15));

        assertThat(responseB.caseId()).isEqualTo(responseA.caseId());
        assertThat(releaseCaseRepository.findByPlan_PlanId(plan.getPlanId())).hasSize(1);
        // 신고 시점에는 아직 매칭 판정을 하지 않는다 - 증빙까지 제출해야 판정된다
        assertThat(confirmerRepository.findById(confirmerA.getConfirmId()).orElseThrow().getReportStatus())
                .isEqualTo(ReportStatus.REPORTED);
        assertThat(confirmerRepository.findById(confirmerB.getConfirmId()).orElseThrow().getReportStatus())
                .isEqualTo(ReportStatus.REPORTED);
    }

    @Test
    void success_wholeFlow_bothReportAndSubmitMatchingEvidence_thenPartnerApprovesBoth_reachesWaiting() {
        createReadyFixture();
        createReviewer();

        DeathReportResponse responseA = report(confirmerA, LocalDate.of(2026, 8, 15));
        DeathReportResponse responseB = report(confirmerB, LocalDate.of(2026, 8, 15)); // 같은 날짜

        Evidence evidenceA = submitEvidence(responseA.caseId(), responseA.evidenceUploadToken(), "proof-a.pdf");
        // 첫 번째 증빙만 들어온 시점에는 아직 매칭 판정이 나지 않는다 (상대가 아직 증빙을 안 냄)
        assertThat(confirmerRepository.findById(confirmerA.getConfirmId()).orElseThrow().getReportStatus())
                .isEqualTo(ReportStatus.REPORTED);

        Evidence evidenceB = submitEvidence(responseB.caseId(), responseB.evidenceUploadToken(), "proof-b.pdf");

        // 두 번째 증빙까지 들어오면 그제서야 매칭 판정 - 날짜가 같으므로 MATCHED
        assertThat(confirmerRepository.findById(confirmerA.getConfirmId()).orElseThrow().getReportStatus())
                .isEqualTo(ReportStatus.MATCHED);
        assertThat(confirmerRepository.findById(confirmerB.getConfirmId()).orElseThrow().getReportStatus())
                .isEqualTo(ReportStatus.MATCHED);

        approve(evidenceA);
        approve(evidenceB);

        ReleaseCase updated = releaseCaseRepository.findById(responseA.caseId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ReleaseCaseStatus.WAITING);
    }

    // ===================== 실패(불일치) 흐름 =====================

    @Test
    void failure_mismatchedDeathDates_marksBothMismatchedButStillStoresEvidence() {
        createReadyFixture();

        DeathReportResponse responseA = report(confirmerA, LocalDate.of(2026, 8, 15));
        DeathReportResponse responseB = report(confirmerB, LocalDate.of(2026, 8, 16)); // 다른 날짜

        submitEvidence(responseA.caseId(), responseA.evidenceUploadToken(), "proof-a.pdf");
        submitEvidence(responseB.caseId(), responseB.evidenceUploadToken(), "proof-b.pdf");

        assertThat(confirmerRepository.findById(confirmerA.getConfirmId()).orElseThrow().getReportStatus())
                .isEqualTo(ReportStatus.MISMATCHED);
        assertThat(confirmerRepository.findById(confirmerB.getConfirmId()).orElseThrow().getReportStatus())
                .isEqualTo(ReportStatus.MISMATCHED);

        // 불일치해도 이미 제출된 증빙 2건은 폐기되지 않고 그대로 남아있어야 한다
        assertThat(evidenceRepository.countByReleaseCase_CaseId(responseA.caseId())).isEqualTo(2);
    }

    @Test
    void failure_mismatchedDeathDates_partnerCanStillApproveBothAndReachWaiting() {
        createReadyFixture();
        createReviewer();

        DeathReportResponse responseA = report(confirmerA, LocalDate.of(2026, 8, 15));
        DeathReportResponse responseB = report(confirmerB, LocalDate.of(2026, 8, 16));

        Evidence evidenceA = submitEvidence(responseA.caseId(), responseA.evidenceUploadToken(), "proof-a.pdf");
        Evidence evidenceB = submitEvidence(responseB.caseId(), responseB.evidenceUploadToken(), "proof-b.pdf");

        // 날짜가 불일치해도 파트너는 자료를 보고 승인할 수 있어야 한다
        approve(evidenceA);
        approve(evidenceB);

        ReleaseCase updated = releaseCaseRepository.findById(responseA.caseId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ReleaseCaseStatus.WAITING);
    }

    @Test
    void failure_partnerCannotApproveBeforeSiblingConfirmerSubmitsEvidence() {
        createReadyFixture();
        createReviewer();

        DeathReportResponse responseA = report(confirmerA, LocalDate.of(2026, 8, 15));
        report(confirmerB, LocalDate.of(2026, 8, 15)); // B는 신고만 하고 증빙은 아직 제출하지 않음

        Evidence evidenceA = submitEvidence(responseA.caseId(), responseA.evidenceUploadToken(), "proof-a.pdf");

        // 상대(B)가 아직 증빙을 내지 않아 매칭 판정이 안 난 상태 - 승인 자체가 막혀야 한다
        assertThatThrownBy(() -> approve(evidenceA))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RELEASE_CASE_INVALID_TRANSITION);

        ReleaseCase updated = releaseCaseRepository.findById(responseA.caseId()).orElseThrow();
        assertThat(updated.getStatus()).isNotEqualTo(ReleaseCaseStatus.WAITING);
    }

    @Test
    void failure_secondEvidenceSubmissionForSameConfirmer_isRejectedAsAlreadySubmitted() {
        createReadyFixture();

        DeathReportResponse responseA = report(confirmerA, LocalDate.of(2026, 8, 15));
        submitEvidence(responseA.caseId(), responseA.evidenceUploadToken(), "proof-a-1.pdf");

        MockMultipartFile secondFile = new MockMultipartFile("file", "proof-a-2.pdf", "application/pdf", PDF_BYTES);
        assertThatThrownBy(() -> evidenceSubmitService.submit(
                responseA.caseId(), responseA.evidenceUploadToken(), secondFile, EvidenceType.DEATH_CERTIFICATE, UUID.randomUUID().toString()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVIDENCE_ALREADY_SUBMITTED);

        assertThat(evidenceRepository.countByReleaseCase_CaseId(responseA.caseId())).isEqualTo(1);
    }
}
