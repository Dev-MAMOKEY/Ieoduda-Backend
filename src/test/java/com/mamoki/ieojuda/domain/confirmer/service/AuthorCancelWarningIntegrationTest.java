package com.mamoki.ieojuda.domain.confirmer.service;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.audit.entity.EmailDeliveryStatus;
import com.mamoki.ieojuda.domain.audit.entity.EmailLog;
import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.audit.repository.EmailLogRepository;
import com.mamoki.ieojuda.domain.confirmer.dto.DeathReportRequest;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.entity.DisputeContact;
import com.mamoki.ieojuda.domain.confirmer.entity.Relationship;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.confirmer.repository.DisputeContactRepository;
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
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.repository.SecurityTokenRepository;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.email.contract.BounceType;
import com.mamoki.ieojuda.global.email.contract.EmailFaill;
import com.mamoki.ieojuda.global.email.contract.EmailSendResult;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxRepository;
import com.mamoki.ieojuda.global.email.sender.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

// 완료 조건 통합 검증 - "사건 생성 시 검증된 작성자 경고 이메일 발송 연결"과 "발송 실패가 성공으로
// 기록되지 않는다"를 실제 스프링 컨텍스트·DB로 검증한다. SMTP만 @MockitoBean으로 격리하고(실제 이메일을
// 보내지 않기 위함 - PartnerReviewAccessHttpIntegrationTest가 EvidenceStorageClient를 격리하는 것과
// 동일한 패턴), 그 외 트랜잭션·리포지토리·엔티티 상태 전이는 전부 실제 경로를 그대로 탄다.
@SpringBootTest
class AuthorCancelWarningIntegrationTest {

    private static final LocalDate REPORTED_DEATH_DATE = LocalDate.of(2026, 8, 15);

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
    private EmailLogRepository emailLogRepository;
    @Autowired
    private EmailOutboxRepository emailOutboxRepository;
    @Autowired
    private DeathReportService deathReportService;
    @Autowired
    private PlatformTransactionManager transactionManager;

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

    // DeathReportReadinessIntegrationTest.createFixture()와 동일한 조건(이의 연락처 인증·대기 기간·
    // 본인 경고 이메일 인증·실행 순서 확정·수락 확인자 2명·봉인)을 갖춘 계획을 만든다 - PlanReadinessValidator를
    // 통과해야 사건 생성까지 도달할 수 있다.
    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(User.builder()
                .email("author-warning-" + UUID.randomUUID() + "@test.com").password("hash").name("김철수").build());
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

        confirmerA = Confirmer.builder().plan(plan).name("A").relationship(Relationship.FRIEND)
                .email("confirmer-a-" + UUID.randomUUID() + "@test.com").build();
        confirmerA.accept(null);
        confirmerA = confirmerRepository.saveAndFlush(confirmerA);
        confirmerB = Confirmer.builder().plan(plan).name("B").relationship(Relationship.FRIEND)
                .email("confirmer-b-" + UUID.randomUUID() + "@test.com").build();
        confirmerB.accept(null);
        confirmerB = confirmerRepository.saveAndFlush(confirmerB);

        disputeContact = DisputeContact.builder().plan(plan)
                .email("dispute-" + UUID.randomUUID() + "@test.com").name("이의연락처").build();
        disputeContact.verify();
        disputeContact = disputeContactRepository.saveAndFlush(disputeContact);

        plan.updateWaitingDays(14);
        plan.requestSelfWarningEmailVerification("warn-" + UUID.randomUUID() + "@test.com", "hash", LocalDateTime.now().plusDays(1));
        plan.verifySelfWarningEmail();
        plan.confirmOrder();
        plan.seal();
        plan = planRepository.saveAndFlush(plan);
    }

    @AfterEach
    void tearDown() {
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
        disputeContactRepository.delete(disputeContact);
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
    }

    private void matchBothConfirmers() {
        String tokenA = securityTokenService.issueForConfirmer(
                SecurityTokenPurpose.REPORT_DEATH, confirmerA, null, LocalDateTime.now().plusDays(1));
        String tokenB = securityTokenService.issueForConfirmer(
                SecurityTokenPurpose.REPORT_DEATH, confirmerB, null, LocalDateTime.now().plusDays(1));
        deathReportService.report(tokenA, new DeathReportRequest(REPORTED_DEATH_DATE), null);
        deathReportService.report(tokenB, new DeathReportRequest(REPORTED_DEATH_DATE), null);
    }

    private EmailLog findCancelWarningLog() {
        return emailLogRepository.findByPlan_PlanIdOrderByRequestedAtDesc(plan.getPlanId()).stream()
                .filter(log -> log.getEmailType() == EmailType.CASE_CANCEL_WARNING)
                .findFirst()
                .orElseThrow(() -> new AssertionError("CASE_CANCEL_WARNING 로그가 기록되지 않았습니다"));
    }

    @Test
    void report_whenAuthorWarningSendSucceeds_caseNotFrozenAndLoggedAsSent() {
        when(emailSender.send(eq(plan.getSelfWarningEmail()), any())).thenReturn(EmailSendResult.success("msg-1"));

        matchBothConfirmers();

        ReleaseCase releaseCase = releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(plan.getPlanId()).orElseThrow();
        assertThat(releaseCase.getFrozen()).isFalse();

        EmailLog log = findCancelWarningLog();
        assertThat(log.getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(log.getRecipientEmail()).isEqualTo(plan.getSelfWarningEmail());
    }

    // 핵심 완료 조건 - 발송 실패가 성공으로 기록되지 않고, 사건은 진행을 멈춘다(동결)
    @Test
    void report_whenAuthorWarningSendFails_freezesCaseAndLogsFailure() {
        when(emailSender.send(eq(plan.getSelfWarningEmail()), any()))
                .thenReturn(EmailSendResult.failure(BounceType.PERMANENT, EmailFaill.INVALID_ADDRESS_FORMAT));

        matchBothConfirmers();

        ReleaseCase releaseCase = releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(plan.getPlanId()).orElseThrow();
        assertThat(releaseCase.getFrozen()).isTrue();

        EmailLog log = findCancelWarningLog();
        assertThat(log.getStatus()).isEqualTo(EmailDeliveryStatus.FAILED);
    }
}
