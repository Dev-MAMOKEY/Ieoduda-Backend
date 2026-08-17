package com.mamoki.ieojuda.domain.confirmer.service;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.audit.entity.EmailLog;
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
import com.mamoki.ieojuda.domain.plan.service.PlanReadinessValidator;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.entity.RoleType;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.repository.SecurityTokenRepository;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.email.outbox.EmailOutbox;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 계획 준비도 게이트 - 실제 스프링 컨텍스트와 DB에 대해 리포지토리 파생 쿼리(이의 연락처 최신 1건,
// 담당자 없는 항목 존재 여부 등)가 실제로 의도대로 동작하는지, 그리고 DeathReportService 경로 전체에서
// 준비되지 않은 계획의 사건 생성이 실제로 막히는지 검증한다. 조건별 분기 로직 자체는
// PlanReadinessValidatorTest(단위 테스트)에서 이미 촘촘히 검증했으므로, 여기서는 DB 연동이 필요한
// 대표 경로만 다룬다.
@SpringBootTest
class DeathReportReadinessIntegrationTest {

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
    private PlanReadinessValidator planReadinessValidator;
    @Autowired
    private DeathReportService deathReportService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private User user;
    private Plan plan;
    private Conversation conversation;
    private LifeArea lifeArea;
    private Recipient recipient;
    private Item item;
    private Confirmer confirmerA;
    private Confirmer confirmerB;
    private DisputeContact disputeContact;

    // 이의 연락처 인증·대기 기간·본인 경고 이메일 인증·실행 순서 확정·수락 확인자 2명까지 전부 충족한
    // 계획을 만든다. sealed=false로 호출하면 "봉인"이라는 조건 하나만 깨진 상태를 재현할 수 있다.
    private void createFixture(boolean sealed) {
        user = userRepository.saveAndFlush(User.builder()
                .email("readiness-" + UUID.randomUUID() + "@test.com").password("hash").name("김철수").build());
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
        if (sealed) {
            plan.seal();
        }
        plan = planRepository.saveAndFlush(plan);
    }

    @AfterEach
    void tearDown() {
        if (plan == null) {
            return;
        }
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
        // 증빙 제출 안내 메일(사건 매칭 시 발송)이 남긴 email_outbox/email_logs 행을 plans FK보다 먼저 지운다
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

    @Test
    void validate_whenAllConditionsMet_doesNotThrow() {
        createFixture(true);

        assertThatCode(() -> planReadinessValidator.validate(plan)).doesNotThrowAnyException();
    }

    @Test
    void validate_whenNoDisputeContactExistsInDatabase_throwsDisputeContactNotVerified() {
        createFixture(true);
        disputeContactRepository.delete(disputeContact);
        disputeContact = null;

        assertThatThrownBy(() -> planReadinessValidator.validate(plan))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DISPUTE_CONTACT_NOT_VERIFIED);
    }

    @Test
    void validate_whenItemInDatabaseHasNoAssignee_throwsItemAssigneeMissing() {
        createFixture(true);
        item.assignRecipient(null);
        item = itemRepository.saveAndFlush(item);

        assertThatThrownBy(() -> planReadinessValidator.validate(plan))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_ASSIGNEE_MISSING);
    }

    @Test
    void validate_whenPlanInDatabaseIsDeactivated_throwsPlanNotReady() {
        createFixture(true);
        plan.deactivate();
        plan = planRepository.saveAndFlush(plan);

        assertThatThrownBy(() -> planReadinessValidator.validate(plan))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PLAN_NOT_READY);
    }

    @Test
    void validate_whenWaitingDaysNotSetInDatabase_throwsWaitingPeriodNotSet() {
        createFixture(true);
        plan.updateWaitingDays(null);
        plan = planRepository.saveAndFlush(plan);

        assertThatThrownBy(() -> planReadinessValidator.validate(plan))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WAITING_PERIOD_NOT_SET);
    }

    @Test
    void validate_whenSelfWarningEmailNotVerifiedInDatabase_throwsSelfWarningEmailNotVerified() {
        createFixture(true);
        plan.invalidateSelfWarningEmailVerification();
        plan = planRepository.saveAndFlush(plan);

        assertThatThrownBy(() -> planReadinessValidator.validate(plan))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SELF_WARNING_EMAIL_NOT_VERIFIED);
    }

    @Test
    void validate_whenOrderNotConfirmedInDatabase_throwsOrderNotConfirmed() {
        createFixture(true);
        plan.resetOrderConfirmation();
        plan = planRepository.saveAndFlush(plan);

        assertThatThrownBy(() -> planReadinessValidator.validate(plan))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_NOT_CONFIRMED);
    }

    @Test
    void report_whenPlanIsNotSealed_blocksReleaseCaseCreationEvenWhenReportsMatch() {
        createFixture(false); // 준비도 조건 중 "봉인"만 깨뜨리고 나머지는 전부 충족시킨다

        String tokenA = securityTokenService.issueForConfirmer(
                SecurityTokenPurpose.REPORT_DEATH, confirmerA, null, LocalDateTime.now().plusDays(1));
        String tokenB = securityTokenService.issueForConfirmer(
                SecurityTokenPurpose.REPORT_DEATH, confirmerB, null, LocalDateTime.now().plusDays(1));

        deathReportService.report(tokenA, new DeathReportRequest(REPORTED_DEATH_DATE), null);

        assertThatThrownBy(() -> deathReportService.report(tokenB, new DeathReportRequest(REPORTED_DEATH_DATE), null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PLAN_NOT_READY);

        assertThat(releaseCaseRepository.findByPlan_PlanId(plan.getPlanId())).isEmpty();
    }

    @Test
    void report_whenAllReadinessConditionsMet_createsReleaseCase() {
        createFixture(true);

        String tokenA = securityTokenService.issueForConfirmer(
                SecurityTokenPurpose.REPORT_DEATH, confirmerA, null, LocalDateTime.now().plusDays(1));
        String tokenB = securityTokenService.issueForConfirmer(
                SecurityTokenPurpose.REPORT_DEATH, confirmerB, null, LocalDateTime.now().plusDays(1));

        deathReportService.report(tokenA, new DeathReportRequest(REPORTED_DEATH_DATE), null);
        deathReportService.report(tokenB, new DeathReportRequest(REPORTED_DEATH_DATE), null);

        assertThat(releaseCaseRepository.findByPlan_PlanId(plan.getPlanId())).hasSize(1);
    }
}
