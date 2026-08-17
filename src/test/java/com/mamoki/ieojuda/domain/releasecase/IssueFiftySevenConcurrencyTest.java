package com.mamoki.ieojuda.domain.releasecase;

import com.mamoki.ieojuda.domain.account.dto.SignupRequest;
import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.account.service.AuthService;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.entity.Relationship;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.evidence.entity.EvidenceType;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceRepository;
import com.mamoki.ieojuda.domain.evidence.service.EvidenceSubmitService;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanVersion;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanVersionRepository;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.storage.EvidenceStorageClient;
import com.mamoki.ieojuda.global.storage.contract.StoredEvidence;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// issue #57 - 실제 DB 제약/잠금이 진짜 동시 요청에서도 지켜지는지 검증한다. Mockito 목으로는 DB 유니크
// 제약이나 비관적 잠금의 효과를 확인할 수 없으므로(#56에서 겪은 트랜잭션 롤백 버그와 같은 종류의 함정),
// 실제 스프링 컨텍스트와 개발 DB에 대해 여러 스레드로 동시에 호출해 검증한다.
@SpringBootTest
class IssueFiftySevenConcurrencyTest {

    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlanRepository planRepository;
    @Autowired
    private PlanVersionRepository planVersionRepository;
    @Autowired
    private ReleaseCaseRepository releaseCaseRepository;
    @Autowired
    private ConfirmerRepository confirmerRepository;
    @Autowired
    private EvidenceRepository evidenceRepository;
    @Autowired
    private EvidenceSubmitService evidenceSubmitService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private EvidenceStorageClient evidenceStorageClient;

    @Test
    void signup_whenTwoRequestsUseSameEmailConcurrently_onlyOneSucceeds() throws Exception {
        String email = "race-" + UUID.randomUUID() + "@test.com";
        // PasswordBreachChecker가 실제 Have I Been Pwned API로 조회하므로, 흔한 값("password1234" 등)이
        // 아니라 유출 목록에 있을 가능성이 거의 없는 무작위 값을 써야 DUPLICATE_EMAIL 분기까지 도달한다.
        String password = "Zx7#" + UUID.randomUUID();
        SignupRequest request = new SignupRequest(email, password, password, "동시가입");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> task = () -> {
            ready.countDown();
            start.await();
            try {
                authService.signup(request);
                return true;
            } catch (CustomException e) {
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_EMAIL);
                return false;
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
            userRepository.findByEmail(email).ifPresent(u -> {
                planRepository.findByUser_UserId(u.getUserId()).ifPresent(planRepository::delete);
                userRepository.delete(u);
            });
        }
    }

    @Test
    void releaseCase_whenTwoConcurrentCreationsTargetTheSamePlan_onlyOneSucceeds() throws Exception {
        User user = userRepository.saveAndFlush(User.builder()
                .email("case-race-" + UUID.randomUUID() + "@test.com").password("hash").name("A").build());
        Plan plan = planRepository.saveAndFlush(Plan.builder().user(user).build());
        PlanVersion version1 = planVersionRepository.saveAndFlush(
                PlanVersion.builder().plan(plan).versionNum(1).snapshotData("{}").build());
        PlanVersion version2 = planVersionRepository.saveAndFlush(
                PlanVersion.builder().plan(plan).versionNum(2).snapshotData("{}").build());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> task1 = () -> attemptCreate(plan, version1, ready, start);
        Callable<Boolean> task2 = () -> attemptCreate(plan, version2, ready, start);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> f1 = pool.submit(task1);
            Future<Boolean> f2 = pool.submit(task2);
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();

            long successCount = List.of(f1.get(10, TimeUnit.SECONDS), f2.get(10, TimeUnit.SECONDS))
                    .stream().filter(Boolean::booleanValue).count();

            // 계획당 취소되지 않은 활성 사건은 DB 부분 유니크 인덱스(uq_release_cases_active_plan)로
            // 하나만 허용된다 - 두 스레드가 동시에 시도해도 실제로 성공하는 건 하나뿐이어야 한다.
            assertThat(successCount).isEqualTo(1);
        } finally {
            pool.shutdown();
            releaseCaseRepository.findByPlan_PlanId(plan.getPlanId()).forEach(releaseCaseRepository::delete);
            planVersionRepository.delete(version1);
            planVersionRepository.delete(version2);
            planRepository.delete(plan);
            userRepository.delete(user);
        }
    }

    private boolean attemptCreate(Plan plan, PlanVersion version, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            releaseCaseRepository.saveAndFlush(ReleaseCase.builder().plan(plan).planVersion(version).build());
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    @Test
    void evidenceSubmit_whenFiveRequestsRaceForTheSameCase_onlyMaxFileCountSucceed() throws Exception {
        when(evidenceStorageClient.store(any(), any()))
                .thenReturn(new StoredEvidence("evidence/race/test", 3));

        User user = userRepository.saveAndFlush(User.builder()
                .email("evidence-race-" + UUID.randomUUID() + "@test.com").password("hash").name("A").build());
        Plan plan = planRepository.saveAndFlush(Plan.builder().user(user).build());
        PlanVersion version = planVersionRepository.saveAndFlush(
                PlanVersion.builder().plan(plan).versionNum(1).snapshotData("{}").build());
        ReleaseCase releaseCase = releaseCaseRepository.saveAndFlush(
                ReleaseCase.builder().plan(plan).planVersion(version).build());
        releaseCase.confirmReport();
        releaseCase.awaitEvidence();

        Confirmer confirmer = Confirmer.builder().plan(plan).name("확인자").relationship(Relationship.FRIEND)
                .email("confirmer-" + UUID.randomUUID() + "@test.com").build();
        String plainToken = "evidence-race-token-" + UUID.randomUUID();
        confirmer.issueInviteToken(TokenProvider.hashToken(plainToken), LocalDateTime.now().plusHours(1));
        confirmer.accept(null);
        confirmerRepository.saveAndFlush(confirmer);

        int attempts = 5;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            List<Future<Boolean>> futures = IntStream.range(0, attempts)
                    .mapToObj(i -> pool.<Boolean>submit(() -> {
                        // TokenLookupGuard -> ClientIpResolver가 스레드 바인딩 HttpServletRequest를 요구하므로,
                        // 실제 웹 요청 없이 별도 스레드에서 서비스를 직접 호출하는 이 테스트에선 각 워커 스레드에
                        // 가짜 요청 컨텍스트를 직접 바인딩해줘야 한다.
                        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
                        try {
                            ready.countDown();
                            start.await();
                            // 매직바이트 검사를 통과해야 하므로 실제 PDF 헤더 바이트를 사용한다.
                            MockMultipartFile file = new MockMultipartFile(
                                    "file", "proof-" + i + ".pdf", "application/pdf",
                                    "%PDF-1.4".getBytes(StandardCharsets.US_ASCII));
                            try {
                                evidenceSubmitService.submit(releaseCase.getCaseId(), plainToken, file, EvidenceType.DEATH_CERTIFICATE, null);
                                return true;
                            } catch (CustomException e) {
                                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVIDENCE_SUBMISSION_INVALID);
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

            // 사건당 최대 3개(MAX_FILE_COUNT) - 동시에 5건이 들어와도 "개수 확인 후 저장" 경쟁 조건 없이
            // 정확히 3건만 성공해야 한다 (사건 행 비관적 잠금으로 직렬화됨).
            assertThat(successCount).isEqualTo(3);
            assertThat(evidenceRepository.countByReleaseCase_CaseId(releaseCase.getCaseId())).isEqualTo(3);
        } finally {
            pool.shutdown();
            evidenceRepository.findByPlan_PlanId(plan.getPlanId()).forEach(evidenceRepository::delete);
            releaseCaseRepository.delete(releaseCase);
            confirmerRepository.delete(confirmer);
            planVersionRepository.delete(version);
            planRepository.delete(plan);
            userRepository.delete(user);
        }
    }

    @Test
    void findDueCasesForUpdateSkipLocked_whenAnotherTransactionHoldsTheLock_skipsItWithoutBlocking() throws Exception {
        User user = userRepository.saveAndFlush(User.builder()
                .email("scheduler-race-" + UUID.randomUUID() + "@test.com").password("hash").name("A").build());
        Plan plan = planRepository.saveAndFlush(Plan.builder().user(user).build());
        PlanVersion version = planVersionRepository.saveAndFlush(
                PlanVersion.builder().plan(plan).versionNum(1).snapshotData("{}").build());
        ReleaseCase dueCase = releaseCaseRepository.saveAndFlush(
                ReleaseCase.builder().plan(plan).planVersion(version).build());
        dueCase.confirmReport();
        dueCase.awaitEvidence();
        dueCase.startEvidenceReview();
        dueCase.approveEvidenceAndStartWaiting(0); // waitingEndsAt = 지금
        // saveAndFlush(detached entity)는 merge()라서 새 관리 인스턴스를 반환한다 - 반드시 재할당해야
        // 이후 delete(dueCase)에서 최신 version을 참조해 낙관적 잠금 충돌이 나지 않는다.
        dueCase = releaseCaseRepository.saveAndFlush(dueCase);

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            Future<List<UUID>> holderFuture = pool.submit(() -> txTemplate.execute(status -> {
                List<UUID> heldIds = releaseCaseRepository.findDueCasesForUpdateSkipLocked(LocalDateTime.now())
                        .stream().map(ReleaseCase::getCaseId).toList();
                locked.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                return heldIds;
            }));

            locked.await(5, TimeUnit.SECONDS);
            long startedAt = System.currentTimeMillis();
            List<UUID> secondAttemptIds = txTemplate.execute(status ->
                    releaseCaseRepository.findDueCasesForUpdateSkipLocked(LocalDateTime.now())
                            .stream().map(ReleaseCase::getCaseId).toList());
            long elapsedMs = System.currentTimeMillis() - startedAt;
            release.countDown();

            List<UUID> firstAttemptIds = holderFuture.get(10, TimeUnit.SECONDS);

            // 첫 번째 트랜잭션이 이 사건을 잠근 상태에서, 두 번째("다른 인스턴스")는 SKIP LOCKED 덕분에
            // 그 사건을 건너뛰고 즉시 반환한다(블로킹 없음) - 같은 사건이 중복으로 처리되지 않는다.
            assertThat(firstAttemptIds).contains(dueCase.getCaseId());
            assertThat(secondAttemptIds).doesNotContain(dueCase.getCaseId());
            assertThat(elapsedMs).isLessThan(3000);
        } finally {
            pool.shutdown();
            releaseCaseRepository.delete(dueCase);
            planVersionRepository.delete(version);
            planRepository.delete(plan);
            userRepository.delete(user);
        }
    }
}
