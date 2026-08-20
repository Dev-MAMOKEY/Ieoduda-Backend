package com.mamoki.ieojuda.global.email.outbox;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.audit.entity.EmailLog;
import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.audit.repository.EmailLogRepository;
import com.mamoki.ieojuda.domain.plan.entity.Conversation;
import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import com.mamoki.ieojuda.domain.plan.entity.LifeAreaCategory;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.ConversationRepository;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.entity.RoleType;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import com.mamoki.ieojuda.domain.stage.repository.HandoverStageRepository;
import com.mamoki.ieojuda.global.email.contract.EmailSendResult;
import com.mamoki.ieojuda.global.email.sender.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// 버그 회귀 방지 - EmailOutboxScheduler는 주석으로 "한 건의 실패가 다른 건의 성공을 롤백시키지
// 않도록 행 단위로 예외를 격리한다"고 되어 있었지만 실제로는 dispatchOne() 안에서 발송 성공 후
// 부수 효과(stage.send())가 던지는 예외를 잡지 않아, dispatchPending() 전체 트랜잭션이 롤백되며
// 같은 배치의 다른 행(이미 성공 처리된 markSent())까지 전부 취소됐다. 실제 운영에서 "OTP가 같은
// 코드로 계속 재발송된다"는 증상이 개별 원인(OTP 자체의 stage 재전이) 수정 이후에도 남아있던 이유 -
// 같은 배치에 걸린 전혀 다른(이미 SENT인 단계를 가리키는) 행 하나가 계속 실패하면 그게 batch 전체를
// 계속 롤백시켜 정상 행까지 끌고 들어갔다.
@SpringBootTest
class EmailOutboxSchedulerIsolationTest {

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
    private HandoverStageRepository handoverStageRepository;
    @Autowired
    private EmailLogRepository emailLogRepository;
    @Autowired
    private EmailOutboxRepository emailOutboxRepository;
    @Autowired
    private EmailOutboxScheduler emailOutboxScheduler;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private EmailSender emailSender;

    private User user;
    private Plan plan;
    private Conversation conversation;
    private LifeArea lifeArea;
    private Recipient recipient;
    private HandoverStage poisonedStage;

    @AfterEach
    void tearDown() {
        if (plan == null) {
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            emailLogRepository.findByPlan_PlanIdOrderByRequestedAtDesc(plan.getPlanId()).forEach(log -> {
                emailOutboxRepository.deleteByEmailLog_LogId(log.getLogId());
                emailLogRepository.delete(log);
            });
        });
        handoverStageRepository.delete(poisonedStage);
        recipientRepository.delete(recipient);
        lifeAreaRepository.delete(lifeArea);
        conversationRepository.delete(conversation);
        planRepository.delete(plan);
        userRepository.delete(user);
    }

    @Test
    void dispatchPending_whenOneRowsPostSendSideEffectThrows_stillCommitsTheOtherRowsInTheBatch() {
        when(emailSender.send(anyString(), any())).thenReturn(EmailSendResult.success("msg-isolation-test"));

        user = userRepository.saveAndFlush(User.builder()
                .email("outbox-isolation-" + UUID.randomUUID() + "@test.com").password("hash").name("김나무").build());
        plan = planRepository.saveAndFlush(Plan.builder().user(user).build());
        conversation = conversationRepository.saveAndFlush(Conversation.builder().plan(plan).build());
        lifeArea = lifeAreaRepository.saveAndFlush(
                LifeArea.builder().plan(plan).conversation(conversation).category(LifeAreaCategory.RELATIONSHIP_CLEANUP).build());
        recipient = recipientRepository.saveAndFlush(Recipient.builder()
                .plan(plan).lifeArea(lifeArea).name("이지수").email("outbox-isolation-recipient-" + UUID.randomUUID() + "@test.com")
                .roleType(RoleType.RELATIONSHIP_MANAGER).isBackup(false)
                .disclosureScope(DisclosureScope.RELATIONSHIP).maxWaitHours(168).backupFor(null).build());

        // "오염된" 행 - 이미 SENT인 단계를 handoverStage로 물고 있어, 발송 성공 후 stage.send()가
        // HANDOVER_STAGE_INVALID_TRANSITION을 던진다 (예: 과거 버그로 잘못 큐에 들어간 행을 재현)
        poisonedStage = handoverStageRepository.saveAndFlush(HandoverStage.builder().plan(plan).recipient(recipient).stageOrder(0).build());
        poisonedStage.send();
        poisonedStage = handoverStageRepository.saveAndFlush(poisonedStage);

        EmailLog poisonedLog = emailLogRepository.save(EmailLog.builder()
                .plan(plan).handoverStage(poisonedStage).emailType(EmailType.POSTHUMOUS_HANDOFF_LINK)
                .recipientEmail(recipient.getEmail()).build());
        emailOutboxRepository.save(EmailOutbox.builder()
                .emailLog(poisonedLog).handoverStage(poisonedStage)
                .recipientEmail(recipient.getEmail()).subject("오염된 메일").body("body").build());

        // 정상 행 - handoverStage 없이(OTP 발송과 동일한 형태) 큐에 들어간, 아무 문제 없는 메일
        EmailLog healthyLog = emailLogRepository.save(EmailLog.builder()
                .plan(plan).handoverStage(null).emailType(EmailType.OTP)
                .recipientEmail(recipient.getEmail()).build());
        EmailOutbox healthyOutbox = emailOutboxRepository.save(EmailOutbox.builder()
                .emailLog(healthyLog).handoverStage(null)
                .recipientEmail(recipient.getEmail()).subject("정상 메일").body("1234").build());

        assertThatCode(() -> emailOutboxScheduler.dispatchPending()).doesNotThrowAnyException();

        EmailOutbox reloadedHealthy = emailOutboxRepository.findById(healthyOutbox.getOutboxId()).orElseThrow();
        assertThat(reloadedHealthy.getStatus()).isEqualTo(EmailOutboxStatus.SENT);
    }
}
