package com.mamoki.ieojuda.domain.postaccess.controller;

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
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxService;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// issue #76 - 실제 컨트롤러/Security 필터 체인/Rate Limit/JSON 직렬화까지 포함한 HTTP 레벨 검증.
// 서비스 단위 테스트(PosthumousAccessServiceTest)만으로는 SecurityConfig의 PERMIT_ALL_PATHS 등록,
// 실제 요청·응답 바디 형태가 맞는지까지는 확인할 수 없어 별도로 둔다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PosthumousAccessHttpIntegrationTest {

    @LocalServerPort
    private int port;

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

    // 실제 SMTP 발송을 막기 위해 EmailOutboxService만 목으로 대체 - 그 외 전부 실제 컨텍스트/DB 사용
    // (issue #51 - 발송이 비동기 아웃박스로 바뀌어, 이 테스트는 실제 발송이 아니라 큐 등록 시점의
    // EmailContent를 검증한다)
    @MockitoBean
    private EmailOutboxService emailOutboxService;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private User user;
    private Plan plan;
    private Recipient recipient;
    private HandoverStage sentStage;

    @BeforeEach
    void setUp() {

        user = userRepository.saveAndFlush(User.builder()
                .email("posthumous-http-" + UUID.randomUUID() + "@test.com").password("hash").name("김나무").build());
        plan = planRepository.saveAndFlush(Plan.builder().user(user).build());
        Conversation conversation = conversationRepository.saveAndFlush(Conversation.builder().plan(plan).build());
        LifeArea lifeArea = lifeAreaRepository.saveAndFlush(
                LifeArea.builder().plan(plan).conversation(conversation).category(LifeAreaCategory.RELATIONSHIP_CLEANUP).build());
        recipient = recipientRepository.saveAndFlush(Recipient.builder()
                .plan(plan).lifeArea(lifeArea).name("이지수").email("jisoo-http-" + UUID.randomUUID() + "@test.com")
                .roleType(RoleType.RELATIONSHIP_MANAGER).isBackup(false)
                .disclosureScope(DisclosureScope.RELATIONSHIP).maxWaitHours(168).backupFor(null).build());
        sentStage = handoverStageRepository.saveAndFlush(HandoverStage.builder().plan(plan).recipient(recipient).stageOrder(0).build());
        sentStage.send();
        sentStage = handoverStageRepository.saveAndFlush(sentStage);
    }

    @AfterEach
    void tearDown() {
        emailLogRepository.findByPlan_PlanIdOrderByRequestedAtDesc(plan.getPlanId()).forEach(emailLogRepository::delete);
        accessTokenRepository.findAll().stream()
                .filter(t -> t.getHandoverStage().getStageId().equals(sentStage.getStageId()))
                .forEach(accessTokenRepository::delete);
        handoverStageRepository.delete(sentStage);
        recipientRepository.delete(recipient);
        lifeAreaRepository.findByPlan_PlanId(plan.getPlanId()).forEach(lifeAreaRepository::delete);
        conversationRepository.findByPlan_PlanId(plan.getPlanId()).forEach(conversationRepository::delete);
        planRepository.delete(plan);
        userRepository.delete(user);
    }

    @Test
    void fullFlow_getAccess_thenOtp_thenVerify_succeedsAndLinkBecomesSingleUse() throws Exception {
        String plainToken = "http-flow-token-" + UUID.randomUUID();
        accessTokenRepository.saveAndFlush(AccessToken.builder()
                .handoverStage(sentStage).tokenHash(TokenProvider.hashToken(plainToken))
                .expiresAt(LocalDateTime.now().plusHours(1)).build());

        HttpResponse<String> getResp = get("/api/posthumous-access/" + plainToken);
        assertThat(getResp.statusCode()).isEqualTo(200);
        assertThat(getResp.body()).contains("\"recipientName\":\"이지수\"");
        assertThat(getResp.body()).contains("\"authorName\":\"김나무\"");
        assertThat(getResp.body()).contains("\"roleLabel\":\"관계 정리 담당자\"");
        assertThat(getResp.body()).contains("\"otpSent\":false");

        HttpResponse<String> otpResp = post("/api/posthumous-access/" + plainToken + "/otp", null);
        assertThat(otpResp.statusCode()).isEqualTo(200);
        assertThat(otpResp.body()).contains("\"maskedEmail\":\"ji***");

        ArgumentCaptor<EmailContent> captor = ArgumentCaptor.forClass(EmailContent.class);
        verify(emailOutboxService).enqueue(any(), any(), any(), anyString(), captor.capture());
        String otpCode = extractOtpCode(captor.getValue().body());

        HttpResponse<String> verifyResp = post("/api/posthumous-access/" + plainToken + "/verify",
                "{\"otpCode\":\"" + otpCode + "\"}");
        assertThat(verifyResp.statusCode()).isEqualTo(200);
        assertThat(verifyResp.body()).contains("\"accessSessionId\":\"" + plainToken + "\"");

        // 인증까지 끝난 링크는 1회성으로 소진되어 재조회가 차단된다
        HttpResponse<String> reGetResp = get("/api/posthumous-access/" + plainToken);
        assertThat(reGetResp.statusCode()).isEqualTo(401);
        assertThat(reGetResp.body()).contains("\"code\":\"ACCESS_LINK_ALREADY_USED\"");
    }

    @Test
    void getAccess_whenExpired_returns401() throws Exception {
        String plainToken = "http-expired-token-" + UUID.randomUUID();
        accessTokenRepository.saveAndFlush(AccessToken.builder()
                .handoverStage(sentStage).tokenHash(TokenProvider.hashToken(plainToken))
                .expiresAt(LocalDateTime.now().minusMinutes(1)).build());

        HttpResponse<String> response = get("/api/posthumous-access/" + plainToken);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"ACCESS_LINK_EXPIRED\"");
    }

    // issue #76 완료 조건 "역할 불일치 차단" - 단계가 아직 SENT 상태가 아니면(대기 중이거나 대체 담당자로
    // 전환됐거나 등) 만료 전 링크라도 차단되어야 한다.
    @Test
    void getAccess_whenHandoverStageNotSent_returns401() throws Exception {
        HandoverStage pendingStage = handoverStageRepository.saveAndFlush(
                HandoverStage.builder().plan(plan).recipient(recipient).stageOrder(1).build()); // send() 호출 안 함
        String plainToken = "http-role-mismatch-token-" + UUID.randomUUID();
        accessTokenRepository.saveAndFlush(AccessToken.builder()
                .handoverStage(pendingStage).tokenHash(TokenProvider.hashToken(plainToken))
                .expiresAt(LocalDateTime.now().plusHours(1)).build());

        try {
            HttpResponse<String> response = get("/api/posthumous-access/" + plainToken);

            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.body()).contains("\"code\":\"ACCESS_LINK_EXPIRED\"");
        } finally {
            accessTokenRepository.findByTokenHash(TokenProvider.hashToken(plainToken)).ifPresent(accessTokenRepository::delete);
            handoverStageRepository.delete(pendingStage);
        }
    }

    @Test
    void verify_whenWrongCodeFiveTimes_locksTokenEvenWithCorrectCodeAfterward() throws Exception {
        String plainToken = "http-lock-token-" + UUID.randomUUID();
        accessTokenRepository.saveAndFlush(AccessToken.builder()
                .handoverStage(sentStage).tokenHash(TokenProvider.hashToken(plainToken))
                .expiresAt(LocalDateTime.now().plusHours(1)).build());

        HttpResponse<String> otpResp = post("/api/posthumous-access/" + plainToken + "/otp", null);
        assertThat(otpResp.statusCode()).isEqualTo(200);

        ArgumentCaptor<EmailContent> captor = ArgumentCaptor.forClass(EmailContent.class);
        verify(emailOutboxService).enqueue(any(), any(), any(), anyString(), captor.capture());
        String realCode = extractOtpCode(captor.getValue().body());
        String wrongCode = realCode.equals("0000") ? "1111" : "0000";

        for (int i = 0; i < 5; i++) {
            HttpResponse<String> failResp = post("/api/posthumous-access/" + plainToken + "/verify",
                    "{\"otpCode\":\"" + wrongCode + "\"}");
            assertThat(failResp.statusCode()).isEqualTo(401);
            assertThat(failResp.body()).contains("\"code\":\"OTP_VERIFICATION_FAILED\"");
        }

        HttpResponse<String> lockedResp = post("/api/posthumous-access/" + plainToken + "/verify",
                "{\"otpCode\":\"" + realCode + "\"}");
        assertThat(lockedResp.statusCode()).isEqualTo(429);
        assertThat(lockedResp.body()).contains("\"code\":\"TOKEN_TEMPORARILY_LOCKED\"");
    }

    private String extractOtpCode(String emailBody) {
        Matcher matcher = Pattern.compile("인증 코드: (\\d{4})").matcher(emailBody);
        assertThat(matcher.find()).as("OTP 이메일 본문에서 4자리 코드를 찾을 수 있어야 한다").isTrue();
        return matcher.group(1);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String jsonBody) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json");
        builder = jsonBody == null
                ? builder.POST(HttpRequest.BodyPublishers.noBody())
                : builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
