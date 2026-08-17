package com.mamoki.ieojuda.domain.evidence.controller;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.entity.Relationship;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanVersion;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanVersionRepository;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.storage.EvidenceStorageClient;
import com.mamoki.ieojuda.global.storage.contract.StoredEvidence;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// issue #88 완료 조건 - "증빙 제출 시 종류를 선택하지 않으면 거부된다"는 것을 실제 컨트롤러/멀티파트
// 파싱 체인까지 통과시켜 검증한다 (서비스 단위 테스트는 파라미터가 항상 채워진 채로 호출되므로,
// 필수 파라미터가 실제로 빠졌을 때 400으로 거부되는지는 이 테스트가 유일하게 검증한다).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EvidenceSubmitHttpIntegrationTest {

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
    private ConfirmerRepository confirmerRepository;
    @Autowired
    private EvidenceRepository evidenceRepository;

    @MockitoBean
    private EvidenceStorageClient evidenceStorageClient;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final String BOUNDARY = "ieoduda-evidence-type-boundary";
    private static final byte[] PDF_BYTES = "%PDF-1.4\n1 0 obj << >> endobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    private User user;
    private Plan plan;
    private PlanVersion planVersion;
    private ReleaseCase releaseCase;
    private Confirmer confirmer;
    private String plainToken;

    @BeforeEach
    void setUp() {
        when(evidenceStorageClient.store(any(), any()))
                .thenReturn(new StoredEvidence("evidence/type-test/uuid.pdf", PDF_BYTES.length));

        user = userRepository.saveAndFlush(User.builder()
                .email("evidence-type-" + UUID.randomUUID() + "@test.com").password("hash").name("김나무").build());
        plan = planRepository.saveAndFlush(Plan.builder().user(user).build());
        planVersion = planVersionRepository.saveAndFlush(
                PlanVersion.builder().plan(plan).versionNum(1).snapshotData("{}").build());
        releaseCase = releaseCaseRepository.saveAndFlush(ReleaseCase.builder().plan(plan).planVersion(planVersion).build());
        releaseCase.confirmReport();
        releaseCase.awaitEvidence();

        confirmer = Confirmer.builder().plan(plan).name("확인자").relationship(Relationship.FRIEND)
                .email("evidence-confirmer-" + UUID.randomUUID() + "@test.com").build();
        plainToken = "evidence-type-token-" + UUID.randomUUID();
        confirmer.issueInviteToken(TokenProvider.hashToken(plainToken), LocalDateTime.now().plusHours(1));
        confirmer.accept(null);
        confirmer = confirmerRepository.saveAndFlush(confirmer);
    }

    @AfterEach
    void tearDown() {
        evidenceRepository.findByPlan_PlanId(plan.getPlanId()).forEach(evidenceRepository::delete);
        confirmerRepository.deleteById(confirmer.getConfirmId());
        releaseCaseRepository.deleteById(releaseCase.getCaseId());
        planVersionRepository.delete(planVersion);
        planRepository.deleteById(plan.getPlanId());
        userRepository.deleteById(user.getUserId());
    }

    @Test
    void submit_withoutEvidenceType_isRejectedWith400() throws Exception {
        HttpResponse<String> response = postMultipart(fileOnlyBody());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"INVALID_INPUT\"");
        assertThat(evidenceRepository.findByPlan_PlanId(plan.getPlanId())).isEmpty();
    }

    @Test
    void submit_withInvalidEvidenceTypeValue_isRejectedWith400() throws Exception {
        HttpResponse<String> response = postMultipart(fileAndFieldBody("evidenceType", "NOT_A_REAL_TYPE"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"INVALID_INPUT\"");
        assertThat(evidenceRepository.findByPlan_PlanId(plan.getPlanId())).isEmpty();
    }

    @Test
    void submit_withValidEvidenceType_isAcceptedAndPersistsTheType() throws Exception {
        HttpResponse<String> response = postMultipart(fileAndFieldBody("evidenceType", "DEATH_CERTIFICATE"));

        assertThat(response.statusCode()).isEqualTo(200);

        List<Evidence> saved = evidenceRepository.findByPlan_PlanId(plan.getPlanId());
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getEvidenceType().name()).isEqualTo("DEATH_CERTIFICATE");
    }

    private byte[] fileOnlyBody() {
        String fileHeader = "--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"proof.pdf\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n";
        String tokenPart = "\r\n--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"token\"\r\n\r\n" + plainToken;
        String closing = "\r\n--" + BOUNDARY + "--\r\n";
        return concat(fileHeader.getBytes(StandardCharsets.UTF_8), PDF_BYTES,
                tokenPart.getBytes(StandardCharsets.UTF_8), closing.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] fileAndFieldBody(String fieldName, String fieldValue) {
        String fileHeader = "--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"proof.pdf\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n";
        String tokenPart = "\r\n--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"token\"\r\n\r\n" + plainToken;
        String fieldPart = "\r\n--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"" + fieldName + "\"\r\n\r\n" + fieldValue;
        String closing = "\r\n--" + BOUNDARY + "--\r\n";
        return concat(fileHeader.getBytes(StandardCharsets.UTF_8), PDF_BYTES,
                tokenPart.getBytes(StandardCharsets.UTF_8),
                fieldPart.getBytes(StandardCharsets.UTF_8), closing.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        byte[] result = new byte[total];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    private HttpResponse<String> postMultipart(byte[] body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/release-cases/" + releaseCase.getCaseId() + "/evidence/submit"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
