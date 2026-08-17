package com.mamoki.ieojuda.domain.plan.controller;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.entity.Relationship;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
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
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.entity.RoleType;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.global.jwt.component.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// issue #81 - 실제 컨트롤러/Security 인증 체인까지 포함한 HTTP 레벨 검증
// (/api/plans/{planId}/packages/**가 SecurityConfig에 별도 permitAll 등록 없이도 기본
// authenticated() 규칙만으로 정상 동작하는지는 실제 JWT로 호출해봐야 확실하다).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlanPackageHttpIntegrationTest {

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
    private ItemRepository itemRepository;
    @Autowired
    private ConfirmerRepository confirmerRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private User user;
    private Plan plan;
    private Recipient recipient;
    private Item item;
    private String accessToken;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("package-http-" + UUID.randomUUID() + "@test.com").password("hash").name("김나무").build();
        user.agreeToConsent(); // ConsentCheckFilter가 미동의 사용자의 보호된 API 접근을 막는다
        user = userRepository.saveAndFlush(user);
        plan = planRepository.saveAndFlush(Plan.builder().user(user).build());
        Conversation conversation = conversationRepository.saveAndFlush(Conversation.builder().plan(plan).build());
        LifeArea lifeArea = lifeAreaRepository.saveAndFlush(
                LifeArea.builder().plan(plan).conversation(conversation).category(LifeAreaCategory.RELATIONSHIP_CLEANUP).build());
        recipient = recipientRepository.saveAndFlush(Recipient.builder()
                .plan(plan).lifeArea(lifeArea).name("이지수").email("jisoo-pkg-" + UUID.randomUUID() + "@test.com")
                .roleType(RoleType.RELATIONSHIP_MANAGER).isBackup(false)
                .disclosureScope(DisclosureScope.RELATIONSHIP).maxWaitHours(168).backupFor(null).build());
        item = itemRepository.saveAndFlush(Item.builder()
                .lifeArea(lifeArea).targetName("이지수").locationType("인스타그램").action("정리")
                .title("SNS 계정 처리").content("비공개로 전환").precondition("")
                .disclosureScope(DisclosureScope.RELATIONSHIP).sourceExcerpt("지수에게 SNS 정리를 부탁")
                .sortOrder(0).actionType(ItemActionType.DELETE).build());
        item.assignRecipient(recipient);
        item = itemRepository.saveAndFlush(item);

        accessToken = jwtTokenProvider.generateAccessToken(user.getUserId(), user.getEmail(), "USER", user.getTokenVersion());
    }

    @AfterEach
    void tearDown() {
        itemRepository.delete(item);
        recipientRepository.delete(recipient);
        lifeAreaRepository.findByPlan_PlanId(plan.getPlanId()).forEach(lifeAreaRepository::delete);
        conversationRepository.findByPlan_PlanId(plan.getPlanId()).forEach(conversationRepository::delete);
        planRepository.delete(plan);
        userRepository.delete(user);
    }

    @Test
    void getPreview_returnsRolePackageWithoutOtherRoles() throws Exception {
        HttpResponse<String> response = get("/api/plans/" + plan.getPlanId() + "/packages/preview");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"recipientName\":\"이지수\"");
        assertThat(response.body()).contains("\"title\":\"SNS 계정 처리\"");
    }

    @Test
    void seal_whenFewerThanTwoConfirmers_returns400WithInsufficientConfirmers() throws Exception {
        HttpResponse<String> response = post("/api/plans/" + plan.getPlanId() + "/packages/seal");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"INSUFFICIENT_CONFIRMERS\"");
    }

    @Test
    void seal_whenAllConditionsMet_sealsThePlanAndPersists() throws Exception {
        Confirmer c1 = Confirmer.builder().plan(plan).name("A").relationship(Relationship.FRIEND).email("a-" + UUID.randomUUID() + "@test.com").build();
        c1.accept(null);
        Confirmer c2 = Confirmer.builder().plan(plan).name("B").relationship(Relationship.FRIEND).email("b-" + UUID.randomUUID() + "@test.com").build();
        c2.accept(null);
        confirmerRepository.saveAndFlush(c1);
        confirmerRepository.saveAndFlush(c2);

        try {
            HttpResponse<String> response = post("/api/plans/" + plan.getPlanId() + "/packages/seal");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"status\":\"SEALED\"");
            assertThat(planRepository.findById(plan.getPlanId()).orElseThrow().getStatus().name()).isEqualTo("SEALED");
        } finally {
            confirmerRepository.delete(c1);
            confirmerRepository.delete(c2);
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(uri(path))
                .header("Authorization", "Bearer " + accessToken)
                .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(uri(path))
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
