package com.mamoki.ieojuda.domain.partner.controller;

import com.mamoki.ieojuda.domain.account.entity.AdminPermission;
import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.entity.UserRole;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.audit.repository.AdminActionAuditLogRepository;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.entity.Relationship;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import com.mamoki.ieojuda.domain.evidence.entity.EvidenceType;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceDownloadTokenRepository;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceRepository;
import com.mamoki.ieojuda.domain.partner.entity.PartnerReviewer;
import com.mamoki.ieojuda.domain.partner.repository.PartnerReviewerRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanVersion;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanVersionRepository;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.global.jwt.component.JwtTokenProvider;
import com.mamoki.ieojuda.global.storage.EvidenceStorageClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// issue #43 완료 조건 통합 검증 - 배정/소속 개념이 없으므로 EVIDENCE_REVIEW 권한을 가진 활성 검토자는
// 누구든 접근할 수 있다. 여기서는 그중 판정을 1회 상태 전이로 제한하는 것과, 원본 다운로드가 1회성
// 토큰을 소비해야 하고 호출자 기준으로 감사되는 것을 실제 HTTP 컨트롤러/재인증 체인 포함해 검증한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PartnerReviewAccessHttpIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlanRepository planRepository;
    @Autowired
    private PlanVersionRepository planVersionRepository;
    @Autowired
    private ReleaseCaseRepository releaseCaseRepository;
    @Autowired
    private PartnerReviewerRepository partnerReviewerRepository;
    @Autowired
    private EvidenceRepository evidenceRepository;
    @Autowired
    private ConfirmerRepository confirmerRepository;
    @Autowired
    private EvidenceDownloadTokenRepository evidenceDownloadTokenRepository;
    @Autowired
    private AdminActionAuditLogRepository adminActionAuditLogRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EvidenceStorageClient evidenceStorageClient;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private User owner;
    private Plan plan;
    private PlanVersion planVersion;
    private ReleaseCase releaseCase;
    private Confirmer confirmer;
    private Evidence evidence;

    private User reviewerAUser;
    private String reviewerAToken;

    private User reviewerBUser; // 배정/소속이 없으므로 reviewerA와 마찬가지로 접근 가능해야 함
    private String reviewerBToken;

    @BeforeEach
    void setUp() {
        when(evidenceStorageClient.load(any())).thenReturn(new byte[]{1, 2, 3});

        owner = userRepository.saveAndFlush(User.builder()
                .email("owner-" + UUID.randomUUID() + "@test.com").password("hash").name("작성자").build());
        plan = planRepository.saveAndFlush(Plan.builder().user(owner).build());
        planVersion = planVersionRepository.saveAndFlush(
                PlanVersion.builder().plan(plan).versionNum(1).snapshotData("{}").build());
        releaseCase = releaseCaseRepository.saveAndFlush(ReleaseCase.builder().plan(plan).planVersion(planVersion).build());

        confirmer = confirmerRepository.saveAndFlush(Confirmer.builder()
                .plan(plan).name("확인자").relationship(Relationship.FRIEND)
                .email("confirmer-access-" + UUID.randomUUID() + "@test.com").build());
        evidence = evidenceRepository.saveAndFlush(Evidence.builder()
                .confirmer(confirmer).plan(plan).releaseCase(releaseCase)
                .storageKey("evidence/access-test/key").fileName("proof.pdf").mimeType("application/pdf")
                .integrityHash("hash").evidenceType(EvidenceType.DEATH_CERTIFICATE).build());

        reviewerAUser = createUser(UserRole.EXTERNAL, Set.of(AdminPermission.EVIDENCE_REVIEW));
        reviewerAToken = issueToken(reviewerAUser);
        partnerReviewerRepository.saveAndFlush(PartnerReviewer.builder()
                .user(reviewerAUser).name("검토자A").email(reviewerAUser.getEmail()).build());

        reviewerBUser = createUser(UserRole.EXTERNAL, Set.of(AdminPermission.EVIDENCE_REVIEW));
        reviewerBToken = issueToken(reviewerBUser);
        partnerReviewerRepository.saveAndFlush(PartnerReviewer.builder()
                .user(reviewerBUser).name("검토자B").email(reviewerBUser.getEmail()).build());
    }

    // 재인증(ReauthGuard)이 실제 BCrypt 비교를 하므로, "hash"라는 원문 비밀번호를 인코딩해서 저장해둔다
    // (테스트에서 결정 API를 password:"hash"로 호출하는 것과 짝을 맞춘다).
    private User createUser(UserRole role, Set<AdminPermission> permissions) {
        User user = User.builder().email(role + "-" + UUID.randomUUID() + "@test.com")
                .password(passwordEncoder.encode("hash")).name(role.name()).build();
        ReflectionTestUtils.setField(user, "role", role);
        ReflectionTestUtils.setField(user, "permissions", permissions);
        return userRepository.saveAndFlush(user);
    }

    private String issueToken(User user) {
        return jwtTokenProvider.generateAccessToken(user.getUserId(), user.getEmail(), user.getRole().name(), user.getTokenVersion());
    }

    @AfterEach
    void tearDown() {
        evidenceDownloadTokenRepository.deleteAll(evidenceDownloadTokenRepository.findByEvidence_EvidenceId(evidence.getEvidenceId()));
        evidenceRepository.deleteById(evidence.getEvidenceId());
        confirmerRepository.deleteById(confirmer.getConfirmId());
        partnerReviewerRepository.deleteAll(partnerReviewerRepository.findAll().stream()
                .filter(r -> r.getUser().getUserId().equals(reviewerAUser.getUserId())
                        || r.getUser().getUserId().equals(reviewerBUser.getUserId()))
                .toList());
        releaseCaseRepository.deleteById(releaseCase.getCaseId());
        planVersionRepository.delete(planVersion);
        planRepository.delete(plan);
        userRepository.delete(owner);
        userRepository.delete(reviewerAUser);
        userRepository.delete(reviewerBUser);
    }

    // issue #120 - 배정/소속 개념이 없다. EVIDENCE_REVIEW 권한을 가진 활성 검토자는 누구든 접근할 수 있다.
    @Test
    void anyActiveReviewerWithPermission_canViewMetadata() throws Exception {
        assertThat(get("/api/partner/reviews/" + evidence.getEvidenceId(), reviewerAToken).statusCode()).isEqualTo(200);
        assertThat(get("/api/partner/reviews/" + evidence.getEvidenceId(), reviewerBToken).statusCode()).isEqualTo(200);
    }

    // 다운로드·판정 이력이 호출자(actor) 기준으로 감사되는지, 다운로드가 1회성인지 검증
    @Test
    void download_isSingleUseAndAuditedByActualCaller() throws Exception {
        HttpResponse<String> linkResponse = post("/api/partner/reviews/" + evidence.getEvidenceId() + "/file", reviewerAToken, null);
        assertThat(linkResponse.statusCode()).isEqualTo(200);
        String token = extractJsonField(linkResponse.body(), "downloadToken");

        HttpResponse<byte[]> fileResponse = httpClient.send(
                HttpRequest.newBuilder(uri("/api/partner/reviews/" + evidence.getEvidenceId() + "/file?token=" + token))
                        .header("Authorization", "Bearer " + reviewerAToken).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(fileResponse.statusCode()).isEqualTo(200);

        boolean audited = adminActionAuditLogRepository.findAllByOrderByOccurredAtDesc(PageRequest.of(0, 20))
                .stream()
                .anyMatch(log -> log.getTargetId().equals(evidence.getEvidenceId())
                        && log.getActorUserId().equals(reviewerAUser.getUserId()));
        assertThat(audited).isTrue();

        // 같은 토큰으로 두 번째 다운로드는 거절되어야 한다(ACCESS_LINK_ALREADY_USED는 이 코드베이스 관례상 401)
        HttpResponse<byte[]> secondAttempt = httpClient.send(
                HttpRequest.newBuilder(uri("/api/partner/reviews/" + evidence.getEvidenceId() + "/file?token=" + token))
                        .header("Authorization", "Bearer " + reviewerAToken).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(secondAttempt.statusCode()).isEqualTo(401);
    }

    // issue #43 완료 조건 - 판정을 1회 상태 전이로 제한한다 (실제 HTTP 컨트롤러/재인증 체인 포함 검증)
    @Test
    void decide_cannotBeChangedAfterAlreadyDecided() throws Exception {
        HttpResponse<String> first = post("/api/partner/reviews/" + evidence.getEvidenceId() + "/decision", reviewerAToken,
                "{\"decision\":\"APPROVE\",\"password\":\"hash\"}");
        assertThat(first.statusCode()).isEqualTo(200);

        HttpResponse<String> second = post("/api/partner/reviews/" + evidence.getEvidenceId() + "/decision", reviewerBToken,
                "{\"decision\":\"REJECT\",\"failureReason\":\"사유\",\"password\":\"hash\"}");
        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(second.body()).contains("EVIDENCE_ALREADY_DECIDED");
    }

    private String extractJsonField(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker) + marker.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(uri(path))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
        builder = body == null
                ? builder.POST(HttpRequest.BodyPublishers.noBody())
                : builder.POST(HttpRequest.BodyPublishers.ofString(body));
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
