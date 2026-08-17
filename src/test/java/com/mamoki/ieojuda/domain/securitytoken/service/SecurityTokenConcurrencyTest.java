package com.mamoki.ieojuda.domain.securitytoken.service;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.entity.Relationship;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityToken;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.repository.SecurityTokenRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// issue #41 완료 조건 - "동시 요청에서도 동일 토큰은 한 번만 사용된다"를 실제 DB에 대해 검증한다.
// Mockito 목으로는 markUsedIfUnused()의 조건부 UPDATE가 실제로 행 잠금 없이도 정확히 하나만
// 성공시키는지 확인할 수 없으므로(issue #57과 같은 함정), 진짜 동시 스레드로 같은 토큰을 소비해본다.
@SpringBootTest
class SecurityTokenConcurrencyTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlanRepository planRepository;
    @Autowired
    private ConfirmerRepository confirmerRepository;
    @Autowired
    private SecurityTokenRepository securityTokenRepository;
    @Autowired
    private SecurityTokenService securityTokenService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void consume_whenTwoConcurrentRequestsUseSameToken_onlyOneSucceeds() throws Exception {
        User user = userRepository.saveAndFlush(User.builder()
                .email("token-race-" + UUID.randomUUID() + "@test.com").password("hash").name("A").build());
        Plan plan = planRepository.saveAndFlush(Plan.builder().user(user).build());
        Confirmer confirmer = confirmerRepository.saveAndFlush(Confirmer.builder()
                .plan(plan).name("확인자").relationship(Relationship.FRIEND)
                .email("token-race-confirmer-" + UUID.randomUUID() + "@test.com").build());

        String plainToken = securityTokenService.issueForConfirmer(
                SecurityTokenPurpose.REPORT_DEATH, confirmer, null, LocalDateTime.now().plusHours(1));

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        // TokenLookupGuard -> ClientIpResolver가 스레드 바인딩 HttpServletRequest를 요구하므로,
        // 실제 웹 요청 없이 별도 스레드에서 서비스를 직접 호출하는 이 테스트에선 각 워커 스레드에
        // 가짜 요청 컨텍스트를 직접 바인딩해줘야 한다.
        Callable<Boolean> task = () -> {
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
            try {
                // consume()이 던지는 예외는 그 자체가 @Transactional 경계라, 같은 물리 트랜잭션 안에서
                // 잡아서 삼키면 이미 rollback-only로 표시된 트랜잭션을 커밋하려다 UnexpectedRollbackException이
                // 난다 - 그래서 예외는 txTemplate.execute() 바깥에서 받아야 한다.
                return txTemplate.execute(status -> {
                    try {
                        ready.countDown();
                        start.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    SecurityToken token = securityTokenService.resolve(plainToken, SecurityTokenPurpose.REPORT_DEATH);
                    securityTokenService.consume(token);
                    return true;
                });
            } catch (CustomException e) {
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_LINK_ALREADY_USED);
                return false;
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> f1 = pool.submit(task);
            Future<Boolean> f2 = pool.submit(task);
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();

            long successCount = List.of(f1.get(10, TimeUnit.SECONDS), f2.get(10, TimeUnit.SECONDS))
                    .stream().filter(Boolean::booleanValue).count();

            assertThat(successCount).isEqualTo(1);
        } finally {
            pool.shutdown();
            new TransactionTemplate(transactionManager).executeWithoutResult(
                    status -> securityTokenRepository.deleteByConfirmer_ConfirmId(confirmer.getConfirmId()));
            confirmerRepository.delete(confirmer);
            planRepository.delete(plan);
            userRepository.delete(user);
        }
    }
}
