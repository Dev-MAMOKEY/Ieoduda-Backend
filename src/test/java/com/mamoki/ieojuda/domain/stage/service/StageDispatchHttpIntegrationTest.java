package com.mamoki.ieojuda.domain.stage.service;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.audit.repository.EmailLogRepository;
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
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.entity.RoleType;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCaseStatus;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStageStatus;
import com.mamoki.ieojuda.domain.stage.repository.HandoverStageRepository;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.contract.EmailSendResult;
import com.mamoki.ieojuda.global.email.sender.EmailSender;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// issue #78 - HandoverStage.complete()가 호출되지 않아 발송 체인이 1단계에서 영구히 멈추던 버그의
// 실제 수정 검증. 서비스 단위 테스트는 HandoverStageService를 목으로 대체하므로, "1단계 완료 -> 2단계
// 메일 발송 -> 마지막 단계 완료 -> ReleaseCase.COMPLETED"라는 전체 체인이 실제로 이어지는지는
// 이 테스트가 유일하게 검증한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StageDispatchHttpIntegrationTest {

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
    private EmailLogRepository emailLogRepository;
    @Autowired
    private PlanSnapshotService planSnapshotService;

    @MockitoBean
    private EmailSender emailSender;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private User user;
    private Plan plan;
    private Recipient recipient1;
    private Recipient recipient2;
    private Item item1;
    private Item item2;
    private HandoverStage stage1;
    private HandoverStage stage2;
    private ReleaseCase releaseCase;
    private PlanVersion planVersion;

    @BeforeEach
    void setUp() {
        when(emailSender.send(anyString(), any(EmailContent.class))).thenReturn(EmailSendResult.success("msg-dispatch-test"));

        user = userRepository.saveAndFlush(User.builder()
                .email("stage-dispatch-" + UUID.randomUUID() + "@test.com").password("hash").name("김나무").build());
        plan = planRepository.saveAndFlush(Plan.builder().user(user).build());
        Conversation conversation = conversationRepository.saveAndFlush(Conversation.builder().plan(plan).build());
        LifeArea lifeArea = lifeAreaRepository.saveAndFlush(
                LifeArea.builder().plan(plan).conversation(conversation).category(LifeAreaCategory.RELATIONSHIP_CLEANUP).build());

        recipient1 = recipientRepository.saveAndFlush(Recipient.builder()
                .plan(plan).lifeArea(lifeArea).name("담당자1").email("stage1-" + UUID.randomUUID() + "@test.com")
                .roleType(RoleType.RELATIONSHIP_MANAGER).isBackup(false)
                .disclosureScope(DisclosureScope.RELATIONSHIP).maxWaitHours(168).backupFor(null).build());
        recipient2 = recipientRepository.saveAndFlush(Recipient.builder()
                .plan(plan).lifeArea(lifeArea).name("담당자2").email("stage2-" + UUID.randomUUID() + "@test.com")
                .roleType(RoleType.WORK_MANAGER).isBackup(false)
                .disclosureScope(DisclosureScope.WORK).maxWaitHours(168).backupFor(null).build());

        item1 = itemRepository.saveAndFlush(Item.builder()
                .lifeArea(lifeArea).targetName("대상1").locationType("이메일").action("정리")
                .title("업무 메일 정리").content("정리").precondition("")
                .disclosureScope(DisclosureScope.RELATIONSHIP).sourceExcerpt("근거")
                .sortOrder(0).actionType(ItemActionType.TRANSFER).build());
        item1.assignRecipient(recipient1);
        item1 = itemRepository.saveAndFlush(item1);

        item2 = itemRepository.saveAndFlush(Item.builder()
                .lifeArea(lifeArea).targetName("대상2").locationType("클라우드").action("이전")
                .title("자료 이전").content("이전").precondition("")
                .disclosureScope(DisclosureScope.WORK).sourceExcerpt("근거2")
                .sortOrder(1).actionType(ItemActionType.TRANSFER).build());
        item2.assignRecipient(recipient2);
        item2 = itemRepository.saveAndFlush(item2);

        PlanSnapshotDto snapshot = planSnapshotService.buildSnapshot(plan);
        String snapshotData = planSnapshotService.serialize(snapshot);
        planVersion = planVersionRepository.saveAndFlush(
                PlanVersion.builder().plan(plan).versionNum(1).snapshotData(snapshotData).build());
        planVersion.seal(planSnapshotService.hash(snapshotData));
        planVersion = planVersionRepository.saveAndFlush(planVersion);

        releaseCase = releaseCaseRepository.saveAndFlush(ReleaseCase.builder().plan(plan).planVersion(planVersion).build());

        stage1 = handoverStageRepository.saveAndFlush(HandoverStage.builder().plan(plan).recipient(recipient1).stageOrder(0).build());
        stage1.assignToCase(releaseCase);
        stage1.send(); // 1단계는 이미 발송된 상태로 시작
        stage1 = handoverStageRepository.saveAndFlush(stage1);

        stage2 = handoverStageRepository.saveAndFlush(HandoverStage.builder().plan(plan).recipient(recipient2).stageOrder(1).build());
        stage2.assignToCase(releaseCase);
        stage2 = handoverStageRepository.saveAndFlush(stage2); // 2단계는 PENDING 그대로 (아직 발송 전)
    }

    @AfterEach
    void tearDown() {
        emailLogRepository.findByPlan_PlanIdOrderBySentAtDesc(plan.getPlanId()).forEach(emailLogRepository::delete);
        packageActionCompletionRepository.findByHandoverStage_StageId(stage1.getStageId()).forEach(packageActionCompletionRepository::delete);
        packageActionCompletionRepository.findByHandoverStage_StageId(stage2.getStageId()).forEach(packageActionCompletionRepository::delete);
        accessTokenRepository.findAll().stream()
                .filter(t -> List.of(stage1.getStageId(), stage2.getStageId()).contains(t.getHandoverStage().getStageId()))
                .forEach(accessTokenRepository::delete);
        // HTTP 호출로 실제 서버가 별도 영속성 컨텍스트에서 stage1/2, releaseCase의 @Version을 올려놨으므로,
        // 이 테스트가 들고 있는(버전이 낡은) 인스턴스로 delete(entity)를 호출하면 낙관적 잠금 충돌이 난다.
        handoverStageRepository.deleteById(stage1.getStageId());
        handoverStageRepository.deleteById(stage2.getStageId());
        releaseCaseRepository.deleteById(releaseCase.getCaseId());
        planVersionRepository.delete(planVersion);
        itemRepository.delete(item1);
        itemRepository.delete(item2);
        recipientRepository.delete(recipient1);
        recipientRepository.delete(recipient2);
        lifeAreaRepository.findByPlan_PlanId(plan.getPlanId()).forEach(lifeAreaRepository::delete);
        conversationRepository.findByPlan_PlanId(plan.getPlanId()).forEach(conversationRepository::delete);
        planRepository.delete(plan);
        userRepository.delete(user);
    }

    private String issueVerifiedSession(HandoverStage stage) {
        String plainToken = "dispatch-session-" + UUID.randomUUID();
        AccessToken accessToken = AccessToken.builder()
                .handoverStage(stage).tokenHash(TokenProvider.hashToken(plainToken))
                .expiresAt(LocalDateTime.now().plusHours(1)).build();
        accessToken.verify();
        accessTokenRepository.saveAndFlush(accessToken);
        return plainToken;
    }

    @Test
    void completingLastActionOfStage_dispatchesNextStage_andCompletesCaseAfterLastStage() throws Exception {
        // 1단계 담당자가 자신의 유일한 행동을 완료 -> 2단계가 자동으로 열려야 한다(완료조건 1)
        String stage1Session = issueVerifiedSession(stage1);
        HttpResponse<String> completeStage1 = post(
                "/api/posthumous-packages/" + stage1Session + "/actions/" + item1.getItemId() + "/complete");
        assertThat(completeStage1.statusCode()).isEqualTo(200);

        HandoverStage reloadedStage1 = handoverStageRepository.findById(stage1.getStageId()).orElseThrow();
        assertThat(reloadedStage1.getStatus()).isEqualTo(HandoverStageStatus.COMPLETED);

        HandoverStage reloadedStage2 = handoverStageRepository.findById(stage2.getStageId()).orElseThrow();
        assertThat(reloadedStage2.getStatus()).isEqualTo(HandoverStageStatus.SENT);
        assertThat(emailLogRepository.findByPlan_PlanIdOrderBySentAtDesc(plan.getPlanId()))
                .anyMatch(log -> log.getHandoverStage().getStageId().equals(stage2.getStageId()));

        // issue #79 - 2단계는 정상적으로 자기 순서가 온 것이지 대체 담당자 전환이 아니므로 최초 발송 문구를 받아야 한다
        org.mockito.ArgumentCaptor<EmailContent> stage2EmailCaptor = org.mockito.ArgumentCaptor.forClass(EmailContent.class);
        verify(emailSender).send(org.mockito.ArgumentMatchers.eq(recipient2.getEmail()), stage2EmailCaptor.capture());
        assertThat(stage2EmailCaptor.getValue().body()).doesNotContain("대체 담당자로 지정되었습니다");
        assertThat(stage2EmailCaptor.getValue().body()).contains("/posthumous-access/");
        assertThat(stage2EmailCaptor.getValue().body()).doesNotContain("/recipient-acceptances/");

        ReleaseCase reloadedCase = releaseCaseRepository.findById(releaseCase.getCaseId()).orElseThrow();
        assertThat(reloadedCase.getStatus()).isNotEqualTo(ReleaseCaseStatus.COMPLETED); // 아직 2단계가 안 끝남

        // 2단계(마지막 단계) 담당자가 완료 -> 사건 전체가 COMPLETED 되어야 한다(완료조건 2)
        String stage2Session = issueVerifiedSession(reloadedStage2);
        HttpResponse<String> completeStage2 = post(
                "/api/posthumous-packages/" + stage2Session + "/actions/" + item2.getItemId() + "/complete");
        assertThat(completeStage2.statusCode()).isEqualTo(200);

        ReleaseCase finalCase = releaseCaseRepository.findById(releaseCase.getCaseId()).orElseThrow();
        assertThat(finalCase.getStatus()).isEqualTo(ReleaseCaseStatus.COMPLETED);
        assertThat(finalCase.getCompletedAt()).isNotNull();
    }

    // issue #78 완료 조건 - "BLOCKED 상태에서는 다음 단계가 자동 활성화되지 않는다"
    @Test
    void whenStageIsBlocked_completingItsActionDoesNotOpenNextStage() throws Exception {
        stage1.block();
        stage1 = handoverStageRepository.saveAndFlush(stage1);
        String plainToken = "blocked-session-" + UUID.randomUUID();
        // BLOCKED 이전에 발급됐던 세션이 아직 살아있는 극단적 상황을 가정해 서비스 계층 가드까지 검증
        AccessToken accessToken = AccessToken.builder()
                .handoverStage(stage1).tokenHash(TokenProvider.hashToken(plainToken))
                .expiresAt(LocalDateTime.now().plusHours(1)).build();
        accessToken.verify();
        accessTokenRepository.saveAndFlush(accessToken);

        post("/api/posthumous-packages/" + plainToken + "/actions/" + item1.getItemId() + "/complete");

        HandoverStage reloadedStage2 = handoverStageRepository.findById(stage2.getStageId()).orElseThrow();
        assertThat(reloadedStage2.getStatus()).isEqualTo(HandoverStageStatus.PENDING); // 열리지 않았어야 한다
        assertThat(emailLogRepository.findByPlan_PlanIdOrderBySentAtDesc(plan.getPlanId()))
                .noneMatch(log -> log.getHandoverStage().getStageId().equals(stage2.getStageId()));
    }

    private HttpResponse<String> post(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(uri(path))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
