package com.mamoki.ieojuda.domain.plan.controller;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.plan.entity.Conversation;
import com.mamoki.ieojuda.domain.plan.entity.LifeAreaMessage;
import com.mamoki.ieojuda.domain.plan.entity.MessageRole;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.ConversationRepository;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaMessageRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
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
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

// 버그 회귀 방지 - LifeAreaMessage.messageId는 GenerationType.UUID(랜덤)라 생성 순서와 무관하다.
// findByConversation_ConversationIdOrderByMessageIdAsc/Desc로 정렬하면 실제 대화 흐름과 무관하게
// 뒤섞인 순서가 나왔다(사용자 발화·AI 응답이 뒤섞여 보이는 문제). createdAt 기준 정렬로 고쳤는지
// 실제 DB에 저장하고 HTTP로 조회해서 검증한다 (mock으로는 정렬 쿼리 자체를 검증할 수 없음).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConversationHistoryOrderingHttpIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlanRepository planRepository;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private LifeAreaMessageRepository lifeAreaMessageRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private User user;
    private Plan plan;
    private Conversation conversation;
    private String accessToken;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("conv-order-" + UUID.randomUUID() + "@test.com").password("hash").name("김나무").build();
        user.agreeToConsent();
        user = userRepository.saveAndFlush(user);
        plan = planRepository.saveAndFlush(Plan.builder().user(user).build());
        conversation = conversationRepository.saveAndFlush(Conversation.builder().plan(plan).build());
        accessToken = jwtTokenProvider.generateAccessToken(user.getUserId(), user.getEmail(), user.getRole().name(), user.getTokenVersion());
    }

    @AfterEach
    void tearDown() {
        lifeAreaMessageRepository.findByConversation_ConversationIdOrderByCreatedAtAscMessageIdAsc(conversation.getConversationId())
                .forEach(lifeAreaMessageRepository::delete);
        conversationRepository.delete(conversation);
        planRepository.delete(plan);
        userRepository.delete(user);
    }

    @Test
    void getHistory_returnsMessagesInActualChronologicalOrder_notRandomUuidOrder() throws Exception {
        // messageId는 랜덤 UUID라 저장 순서와 뒤섞일 수 있으므로, 실제 대화가 오간 순서 그대로
        // (아주 짧은 간격을 두고) 저장해 createdAt이 서로 달라지게 만든다.
        List<String> expectedOrder = List.of(
                "1번째 사용자 발화", "1번째 AI 응답", "2번째 사용자 발화", "2번째 AI 응답", "3번째 사용자 발화"
        );
        List<MessageRole> roles = List.of(
                MessageRole.USER, MessageRole.ASSISTANT, MessageRole.USER, MessageRole.ASSISTANT, MessageRole.USER
        );
        for (int i = 0; i < expectedOrder.size(); i++) {
            lifeAreaMessageRepository.saveAndFlush(LifeAreaMessage.builder()
                    .conversation(conversation).role(roles.get(i)).content(expectedOrder.get(i)).build());
            Thread.sleep(5); // createdAt 값이 확실히 서로 달라지도록 간격을 둔다
        }

        HttpResponse<String> response = get(
                "/api/plans/" + plan.getPlanId() + "/conversations/" + conversation.getConversationId() + "/messages?page=0&size=20");
        assertThat(response.statusCode()).isEqualTo(200);

        List<String> actualOrder = extractContentsInOrder(response.body());
        assertThat(actualOrder).containsExactlyElementsOf(expectedOrder);
    }

    // 응답 JSON에서 "content":"..." 값을 등장 순서 그대로 뽑아낸다 (전용 JSON 파서 의존성 없이 정규식으로 충분)
    private List<String> extractContentsInOrder(String body) {
        List<String> contents = new java.util.ArrayList<>();
        Matcher matcher = Pattern.compile("\"content\":\"([^\"]*)\"").matcher(body);
        while (matcher.find()) {
            contents.add(matcher.group(1));
        }
        return contents;
    }

    private HttpResponse<String> get(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(uri(path))
                .header("Authorization", "Bearer " + accessToken).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
