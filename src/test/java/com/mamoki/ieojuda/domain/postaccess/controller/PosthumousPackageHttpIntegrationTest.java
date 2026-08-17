package com.mamoki.ieojuda.domain.postaccess.controller;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.plan.dto.PlanSnapshotDto;
import com.mamoki.ieojuda.domain.plan.entity.Conversation;
import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.plan.entity.ItemActionType;
import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import com.mamoki.ieojuda.domain.plan.entity.LifeAreaCategory;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanVersion;
import com.mamoki.ieojuda.domain.plan.repository.ConversationRepository;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanVersionRepository;
import com.mamoki.ieojuda.domain.plan.service.PlanSnapshotService;
import com.mamoki.ieojuda.domain.postaccess.entity.AccessToken;
import com.mamoki.ieojuda.domain.postaccess.repository.AccessTokenRepository;
import com.mamoki.ieojuda.domain.postaccess.repository.PackageActionCompletionRepository;
import com.mamoki.ieojuda.domain.postaccess.repository.PackageIssueRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.entity.RoleType;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import com.mamoki.ieojuda.domain.stage.repository.HandoverStageRepository;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// issue #77 - 실제 컨트롤러/Security 필터 체인/Idempotency-Key DB 유니크 제약까지 포함한 HTTP 레벨 검증.
// 서비스 단위 테스트(Mockito)는 IdempotencyGuard를 목으로 대체하므로, 실제 중복 요청 차단이 되는지는
// 이 테스트가 유일하게 검증한다 (issue #76에서 Mockito가 트랜잭션 롤백을 못 잡아냈던 것과 같은 이유).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PosthumousPackageHttpIntegrationTest {

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
    private PlanVersionRepository planVersionRepository;
    @Autowired
    private ReleaseCaseRepository releaseCaseRepository;
    @Autowired
    private HandoverStageRepository handoverStageRepository;
    @Autowired
    private AccessTokenRepository accessTokenRepository;
    @Autowired
    private PackageActionCompletionRepository packageActionCompletionRepository;
    @Autowired
    private PackageIssueRepository packageIssueRepository;
    @Autowired
    private PlanSnapshotService planSnapshotService;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private User user;
    private Plan plan;
    private Recipient recipient;
    private Item item;
    private HandoverStage stage;
    private ReleaseCase releaseCase;
    private PlanVersion planVersion;

    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(User.builder()
                .email("posthumous-pkg-http-" + UUID.randomUUID() + "@test.com").password("hash").name("김나무").build());
        plan = planRepository.saveAndFlush(Plan.builder().user(user).build());
        Conversation conversation = conversationRepository.saveAndFlush(Conversation.builder().plan(plan).build());
        LifeArea lifeArea = lifeAreaRepository.saveAndFlush(
                LifeArea.builder().plan(plan).conversation(conversation).category(LifeAreaCategory.RELATIONSHIP_CLEANUP).build());
        recipient = recipientRepository.saveAndFlush(Recipient.builder()
                .plan(plan).lifeArea(lifeArea).name("이지수").email("jisoo-pkg-http-" + UUID.randomUUID() + "@test.com")
                .roleType(RoleType.RELATIONSHIP_MANAGER).isBackup(false)
                .disclosureScope(DisclosureScope.RELATIONSHIP).maxWaitHours(168).backupFor(null).build());
        item = itemRepository.saveAndFlush(Item.builder()
                .lifeArea(lifeArea).targetName("이지수").locationType("인스타그램").action("비공개 전환")
                .title("SNS 계정 처리").content("비공개로 전환").precondition("")
                .disclosureScope(DisclosureScope.RELATIONSHIP).sourceExcerpt("근거")
                .sortOrder(0).actionType(ItemActionType.DELETE).build());
        item.assignRecipient(recipient);
        item = itemRepository.saveAndFlush(item);

        PlanSnapshotDto snapshot = planSnapshotService.buildSnapshot(plan);
        String snapshotData = planSnapshotService.serialize(snapshot);
        planVersion = planVersionRepository.saveAndFlush(
                PlanVersion.builder().plan(plan).versionNum(1).snapshotData(snapshotData).build());
        planVersion.seal(planSnapshotService.hash(snapshotData));
        planVersion = planVersionRepository.saveAndFlush(planVersion);

        releaseCase = releaseCaseRepository.saveAndFlush(ReleaseCase.builder().plan(plan).planVersion(planVersion).build());

        stage = handoverStageRepository.saveAndFlush(HandoverStage.builder().plan(plan).recipient(recipient).stageOrder(0).build());
        stage.assignToCase(releaseCase);
        stage.send();
        stage = handoverStageRepository.saveAndFlush(stage);
    }

    @AfterEach
    void tearDown() {
        packageIssueRepository.findByRecipient_Plan_PlanId(plan.getPlanId()).forEach(packageIssueRepository::delete);
        packageActionCompletionRepository.findByHandoverStage_StageId(stage.getStageId()).forEach(packageActionCompletionRepository::delete);
        accessTokenRepository.findAll().stream()
                .filter(t -> t.getHandoverStage().getStageId().equals(stage.getStageId()))
                .forEach(accessTokenRepository::delete);
        // issue #78 이후 행동 완료가 단계·사건까지 완료 처리할 수 있어(@Version 증가), 이 테스트가 들고
        // 있는 인스턴스로 delete(entity)를 호출하면 낙관적 잠금 충돌이 날 수 있다 - ID 기준으로 삭제한다.
        handoverStageRepository.deleteById(stage.getStageId());
        releaseCaseRepository.deleteById(releaseCase.getCaseId());
        planVersionRepository.delete(planVersion);
        itemRepository.delete(item);
        recipientRepository.delete(recipient);
        lifeAreaRepository.findByPlan_PlanId(plan.getPlanId()).forEach(lifeAreaRepository::delete);
        conversationRepository.findByPlan_PlanId(plan.getPlanId()).forEach(conversationRepository::delete);
        planRepository.delete(plan);
        userRepository.delete(user);
    }

    private String issueVerifiedSession() {
        String plainToken = "pkg-session-" + UUID.randomUUID();
        AccessToken accessToken = AccessToken.builder()
                .handoverStage(stage).tokenHash(TokenProvider.hashToken(plainToken))
                .expiresAt(LocalDateTime.now().plusHours(1)).build();
        accessToken.verify();
        accessTokenRepository.saveAndFlush(accessToken);
        return plainToken;
    }

    @Test
    void getPackage_returnsMyItemOnly() throws Exception {
        String sessionId = issueVerifiedSession();

        HttpResponse<String> response = get("/api/posthumous-packages/" + sessionId);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"roleLabel\":\"관계 정리 담당자\"");
        assertThat(response.body()).contains("\"totalCount\":1");
        assertThat(response.body()).contains("\"actionId\":" + item.getItemId());
        assertThat(response.body()).contains("\"action\":\"비공개 전환\""); // 이슈/노션이 요구하는 "행동" 필드
        assertThat(response.body()).doesNotContain("근거"); // sourceExcerpt(원문 근거)는 응답에 없어야 함
    }

    @Test
    void getPackage_whenSessionNotVerified_returns401() throws Exception {
        String plainToken = "pkg-unverified-" + UUID.randomUUID();
        accessTokenRepository.saveAndFlush(AccessToken.builder()
                .handoverStage(stage).tokenHash(TokenProvider.hashToken(plainToken))
                .expiresAt(LocalDateTime.now().plusHours(1)).build()); // verify() 호출 안 함

        HttpResponse<String> response = get("/api/posthumous-packages/" + plainToken);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"ACCESS_LINK_EXPIRED\"");
    }

    @Test
    void completeAction_withSameIdempotencyKeyTwice_secondCallRejectedAsDuplicate() throws Exception {
        String sessionId = issueVerifiedSession();
        String idempotencyKey = UUID.randomUUID().toString();
        String path = "/api/posthumous-packages/" + sessionId + "/actions/" + item.getItemId() + "/complete";

        HttpResponse<String> first = postWithIdempotencyKey(path, idempotencyKey);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.body()).contains("\"status\":\"COMPLETED\"");
        assertThat(packageActionCompletionRepository.findByHandoverStage_StageIdAndItemId(stage.getStageId(), item.getItemId()))
                .isPresent();

        HttpResponse<String> second = postWithIdempotencyKey(path, idempotencyKey);
        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(second.body()).contains("\"code\":\"DUPLICATE_REQUEST\"");
        // 실제로 완료 기록은 하나만 남아야 한다 (중복 저장되지 않음)
        assertThat(packageActionCompletionRepository.findByHandoverStage_StageId(stage.getStageId())).hasSize(1);
    }

    @Test
    void reportIssue_savesAndIsQueryable() throws Exception {
        String sessionId = issueVerifiedSession();

        HttpResponse<String> response = post("/api/posthumous-packages/" + sessionId + "/issues",
                "{\"actionId\":" + item.getItemId() + ",\"reason\":\"해당 계정에 접근할 수 없습니다.\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"OPEN\"");
        assertThat(packageIssueRepository.findByRecipient_Plan_PlanId(plan.getPlanId())).hasSize(1);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String jsonBody) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postWithIdempotencyKey(String path, String key) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(uri(path))
                .header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
