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
import com.mamoki.ieojuda.domain.stage.repository.HandoverStageRepository;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxRepository;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

// 백엔드 코드 리뷰로 발견 - sendOtp()가 재발송 쿨다운을 잠금 없이 확인해서, 같은 토큰으로 거의 동시에
// 여러 요청이 들어오면 전부 같은(갱신 전) otpSentAt을 읽고 쿨다운을 동시에 통과해 OTP 메일이 요청 수만큼
// 중복 발송됐다. findByTokenHashForUpdate()로 비관적 잠금을 걸어, 동시 요청 중 하나만 실제로 발송하고
// 나머지는 갱신된 otpSentAt으로 쿨다운에 막히는지 검증한다.
@SpringBootTest
class PosthumousAccessOtpConcurrencyTest {

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
    private PosthumousAccessService posthumousAccessService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void sendOtp_whenFiveRequestsRaceForTheSameToken_onlyOneEnqueuesAnEmail() throws Exception {
        User user = userRepository.saveAndFlush(User.builder()
                .email("otp-race-" + UUID.randomUUID() + "@test.com").password("hash").name("김나무").build());
        Plan plan = planRepository.saveAndFlush(Plan.builder().user(user).build());
        Conversation conversation = conversationRepository.saveAndFlush(Conversation.builder().plan(plan).build());
        LifeArea lifeArea = lifeAreaRepository.saveAndFlush(
                LifeArea.builder().plan(plan).conversation(conversation).category(LifeAreaCategory.RELATIONSHIP_CLEANUP).build());
        String recipientEmail = "otp-race-recipient-" + UUID.randomUUID() + "@test.com";
        Recipient recipient = recipientRepository.saveAndFlush(Recipient.builder()
                .plan(plan).lifeArea(lifeArea).name("이지수").email(recipientEmail)
                .roleType(RoleType.RELATIONSHIP_MANAGER).isBackup(false)
                .disclosureScope(DisclosureScope.RELATIONSHIP).maxWaitHours(168).backupFor(null).build());
        HandoverStage stage = handoverStageRepository.saveAndFlush(
                HandoverStage.builder().plan(plan).recipient(recipient).stageOrder(0).build());
        stage.send();
        stage = handoverStageRepository.saveAndFlush(stage);

        String plainToken = "otp-race-token-" + UUID.randomUUID();
        accessTokenRepository.saveAndFlush(AccessToken.builder()
                .handoverStage(stage).tokenHash(TokenProvider.hashToken(plainToken))
                .expiresAt(LocalDateTime.now().plusHours(1)).build());

        int attempts = 5;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            List<Future<Boolean>> futures = IntStream.range(0, attempts)
                    .mapToObj(i -> pool.<Boolean>submit(() -> {
                        // TokenLookupGuard -> ClientIpResolver가 스레드 바인딩 HttpServletRequest를 요구한다
                        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
                        try {
                            ready.countDown();
                            start.await();
                            try {
                                posthumousAccessService.sendOtp(plainToken);
                                return true;
                            } catch (Exception e) {
                                return false;
                            }
                        } finally {
                            RequestContextHolder.resetRequestAttributes();
                        }
                    }))
                    .toList();
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();

            long successCount = 0;
            for (Future<Boolean> f : futures) {
                if (f.get(10, TimeUnit.SECONDS)) {
                    successCount++;
                }
            }

            // 잠금 없이는 5건 모두 쿨다운을 동시에 통과해 5건 다 발송됐다 - 잠금이 걸리면 정확히 1건만 통과한다.
            assertThat(successCount).isEqualTo(1);
            long enqueuedCount = emailOutboxRepository.findAll().stream()
                    .filter(o -> o.getRecipientEmail().equals(recipientEmail))
                    .count();
            assertThat(enqueuedCount).isEqualTo(1);
        } finally {
            pool.shutdown();
            Plan finalPlan = plan;
            HandoverStage finalStage = stage;
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    emailLogRepository.findByPlan_PlanIdOrderByRequestedAtDesc(finalPlan.getPlanId())
                            .forEach(log -> {
                                emailOutboxRepository.deleteByEmailLog_LogId(log.getLogId());
                                emailLogRepository.delete(log);
                            }));
            accessTokenRepository.findAll().stream()
                    .filter(t -> t.getHandoverStage().getStageId().equals(finalStage.getStageId()))
                    .forEach(accessTokenRepository::delete);
            handoverStageRepository.delete(finalStage);
            recipientRepository.delete(recipient);
            lifeAreaRepository.delete(lifeArea);
            conversationRepository.delete(conversation);
            planRepository.delete(plan);
            userRepository.delete(user);
        }
    }
}
