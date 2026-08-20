package com.mamoki.ieojuda.domain.account.controller;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
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

// 필수동의(consentAgreedAt)를 하지 않은 사용자도 GET /users/me(내 정보 조회)는 할 수 있어야 하지만,
// PUT/DELETE /users/me는 여전히 ConsentCheckFilter가 막아야 한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConsentCheckFilterHttpIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private User user;
    private String accessToken;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("consent-filter-" + UUID.randomUUID() + "@test.com").password("hash").name("김미동의").build();
        // 일부러 agreeToConsent()를 호출하지 않아 필수동의 미완료 상태로 둔다.
        user = userRepository.saveAndFlush(user);
        accessToken = jwtTokenProvider.generateAccessToken(user.getUserId(), user.getEmail(), "USER", user.getTokenVersion());
    }

    @AfterEach
    void tearDown() {
        userRepository.delete(user);
    }

    @Test
    void getMe_whenConsentNotAgreed_returns200() throws Exception {
        HttpResponse<String> response = get("/users/me");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"name\":\"김미동의\"");
    }

    @Test
    void updateMe_whenConsentNotAgreed_returns400WithConsentRequired() throws Exception {
        String body = "{\"name\":\"바뀐이름\"}";
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(uri("/users/me"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"CONSENT_REQUIRED\"");
    }

    @Test
    void deleteMe_whenConsentNotAgreed_returns400WithConsentRequired() throws Exception {
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(uri("/users/me"))
                .header("Authorization", "Bearer " + accessToken)
                .DELETE().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"CONSENT_REQUIRED\"");
    }

    // 필수동의를 이미 완료한 사용자는 이번 변경 전과 동일하게 세 API 모두 정상 동작해야 한다(회귀 방지).
    // 공용 user/accessToken을 건드리면(특히 삭제) 다른 테스트와 상태가 얽히므로 각자 별도 사용자를 쓴다.

    @Test
    void getMe_whenConsentAgreed_returns200() throws Exception {
        User consentedUser = createConsentedUser("김동의-조회");
        try {
            HttpResponse<String> response = getWithToken("/users/me", tokenFor(consentedUser));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"name\":\"김동의-조회\"");
        } finally {
            userRepository.delete(consentedUser);
        }
    }

    @Test
    void updateMe_whenConsentAgreed_returns200AndUpdatesProfile() throws Exception {
        User consentedUser = createConsentedUser("김동의-수정전");
        try {
            // 이메일은 그대로 두고 이름만 바꿔서 이메일 변경에 딸린 별도 정책(세션 폐기 등)은 건드리지 않는다.
            String body = "{\"email\":\"" + consentedUser.getEmail() + "\",\"name\":\"김동의-수정후\"}";
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(uri("/users/me"))
                    .header("Authorization", "Bearer " + tokenFor(consentedUser))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"name\":\"김동의-수정후\"");
            assertThat(userRepository.findById(consentedUser.getUserId()).orElseThrow().getName())
                    .isEqualTo("김동의-수정후");
        } finally {
            userRepository.deleteById(consentedUser.getUserId());
        }
    }

    @Test
    void deleteMe_whenConsentAgreed_returns200AndDeletesAccount() throws Exception {
        User consentedUser = createConsentedUser("김동의-삭제");

        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(uri("/users/me"))
                .header("Authorization", "Bearer " + tokenFor(consentedUser))
                .DELETE().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(userRepository.findById(consentedUser.getUserId())).isEmpty();
    }

    private User createConsentedUser(String name) {
        User consentedUser = User.builder()
                .email("consent-filter-agreed-" + UUID.randomUUID() + "@test.com").password("hash").name(name).build();
        consentedUser.agreeToConsent();
        return userRepository.saveAndFlush(consentedUser);
    }

    private String tokenFor(User targetUser) {
        return jwtTokenProvider.generateAccessToken(
                targetUser.getUserId(), targetUser.getEmail(), "USER", targetUser.getTokenVersion());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return getWithToken(path, accessToken);
    }

    private HttpResponse<String> getWithToken(String path, String token) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(uri(path))
                .header("Authorization", "Bearer " + token)
                .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
