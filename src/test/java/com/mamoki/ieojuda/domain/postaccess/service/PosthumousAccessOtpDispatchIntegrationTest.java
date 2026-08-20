package com.mamoki.ieojuda.domain.postaccess.service;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.audit.repository.EmailLogRepository;
import com.mamoki.ieojuda.domain.plan.entity.Conversation;
import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import com.mamoki.ieojuda.domain.plan.entity.LifeAreaCategory;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.ConversationRepository;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.domain.postaccess.entity.AccessToken;
import com.mamoki.ieojuda.domain.postaccess.repository.AccessTokenRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.entity.RoleType;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStageStatus;
import com.mamoki.ieojuda.domain.stage.repository.HandoverStageRepository;
import com.mamoki.ieojuda.global.email.contract.EmailSendResult;
import com.mamoki.ieojuda.global.email.outbox.EmailOutbox;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxRepository;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxScheduler;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxStatus;
import com.mamoki.ieojuda.global.email.sender.EmailSender;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// 프로덕션에서 실제로 보고된 버그의 회귀 방지 - OTP 메일이 같은 코드로 계속(스케줄러 주기마다) 재발송됐다.
// 원인: sendOtp()가 큐에 넣을 때 accessToken.getHandoverStage()를 그대로 넘겨서, 발송 성공 시
// EmailOutboxScheduler가 stage.send()를 다시 호출했다. sendOtp()에 도달하려면 그 단계는 이미
// SENT 상태(checkRoleConsistent)이므로 send()의 허용 전이(PENDING|READY|FALLBACK -> SENT)에
// 걸려 HANDOVER_STAGE_INVALID_TRANSITION이 터졌고, 그 예외가 dispatchPending()의 트랜잭션
// 전체를 롤백시켜 outbox.markSent()까지 취소됐다 - 그래서 같은 행이 PENDING으로 영원히 남아
// 다음 주기마다 같은 본문(같은 코드)으로 계속 재발송됐다. 단위 테스트(Mockito)로는 이 롤백을
// 잡을 수 없어서 실제 DB/트랜잭션을 쓰는 통합 테스트로만 검증할 수 있다.
@SpringBootTest
class PosthumousAccessOtpDispatchIntegrationTest {

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
    private AccessTokenRepository accessTokenRepository;
    @Autowired
    private EmailLogRepository emailLogRepository;
    @Autowired
    private EmailOutboxRepository emailOutboxRepository;
    @Autowired
    private EmailOutboxScheduler emailOutboxScheduler;
    @Autowired
    private PosthumousAccessService posthumousAccessService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private EmailSender emailSender;

    private User user;
    private Plan plan;
    private Conversation conversation;
    private LifeArea lifeArea;
    private Recipient recipient;
    private HandoverStage stage;

    @AfterEach
    void tearDown() {
        if (plan == null) {
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                emailLogRepository.findByPlan_PlanIdOrderByRequestedAtDesc(plan.getPlanId()).forEach(log -> {
                    emailOutboxRepository.deleteByEmailLog_LogId(log.getLogId());
                    emailLogRepository.delete(log);
                }));
        accessTokenRepository.findAll().stream()
                .filter(t -> t.getHandoverStage().getStageId().equals(stage.getStageId()))
                .forEach(accessTokenRepository::delete);
        handoverStageRepository.delete(stage);
        recipientRepository.delete(recipient);
        lifeAreaRepository.delete(lifeArea);
        conversationRepository.delete(conversation);
        planRepository.delete(plan);
        userRepository.delete(user);
    }

    @Test
    void dispatchPending_whenOtpEmailSucceeds_doesNotRetryOrTouchStage() {
        when(emailSender.send(anyString(), any())).thenReturn(EmailSendResult.success("msg-otp-dispatch-test"));

        user = userRepository.saveAndFlush(User.builder()
                .email("otp-dispatch-" + UUID.randomUUID() + "@test.com").password("hash").name("김나무").build());
        plan = planRepository.saveAndFlush(Plan.builder().user(user).build());
        conversation = conversationRepository.saveAndFlush(Conversation.builder().plan(plan).build());
        lifeArea = lifeAreaRepository.saveAndFlush(
                LifeArea.builder().plan(plan).conversation(conversation).category(LifeAreaCategory.RELATIONSHIP_CLEANUP).build());
        recipient = recipientRepository.saveAndFlush(Recipient.builder()
                .plan(plan).lifeArea(lifeArea).name("이지수").email("otp-dispatch-recipient-" + UUID.randomUUID() + "@test.com")
                .roleType(RoleType.RELATIONSHIP_MANAGER).isBackup(false)
                .disclosureScope(DisclosureScope.RELATIONSHIP).maxWaitHours(168).backupFor(null).build());
        stage = handoverStageRepository.saveAndFlush(HandoverStage.builder().plan(plan).recipient(recipient).stageOrder(0).build());
        stage.send(); // sendOtp()는 checkRoleConsistent()가 SENT를 요구하므로 미리 SENT로 만들어 둔다
        stage = handoverStageRepository.saveAndFlush(stage);

        String plainToken = "otp-dispatch-token-" + UUID.randomUUID();
        accessTokenRepository.saveAndFlush(AccessToken.builder()
                .handoverStage(stage).tokenHash(TokenProvider.hashToken(plainToken))
                .expiresAt(LocalDateTime.now().plusHours(1)).build());

        posthumousAccessService.sendOtp(plainToken);

        // 발송 처리 자체가 예외 없이 끝나야 한다 (과거 버그: stage.send() 재호출로
        // HANDOVER_STAGE_INVALID_TRANSITION이 터져 트랜잭션이 롤백됐음)
        assertThatCode(() -> emailOutboxScheduler.dispatchPending()).doesNotThrowAnyException();

        List<EmailOutbox> outboxRows = emailOutboxRepository.findAll().stream()
                .filter(o -> o.getRecipientEmail().equals(recipient.getEmail()))
                .toList();
        assertThat(outboxRows).hasSize(1);
        assertThat(outboxRows.get(0).getStatus()).isEqualTo(EmailOutboxStatus.SENT);

        // 단계는 발송 전과 동일하게 SENT로 유지되어야 한다 - 두 번째로 send()가 호출되지 않았다는 뜻
        HandoverStage reloadedStage = handoverStageRepository.findById(stage.getStageId()).orElseThrow();
        assertThat(reloadedStage.getStatus()).isEqualTo(HandoverStageStatus.SENT);

        // 다음 주기에 다시 실행해도 재전송할 PENDING 행이 남아 있지 않아야 한다
        assertThatCode(() -> emailOutboxScheduler.dispatchPending()).doesNotThrowAnyException();
        long stillPending = emailOutboxRepository.findAll().stream()
                .filter(o -> o.getRecipientEmail().equals(recipient.getEmail()))
                .filter(o -> o.getStatus() == EmailOutboxStatus.PENDING)
                .count();
        assertThat(stillPending).isZero();
    }
}
