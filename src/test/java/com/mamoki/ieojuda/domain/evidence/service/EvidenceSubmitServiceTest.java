package com.mamoki.ieojuda.domain.evidence.service;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCaseStatus;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import com.mamoki.ieojuda.domain.evidence.entity.EvidenceType;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceRepository;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityToken;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.idempotency.service.IdempotencyGuard;
import com.mamoki.ieojuda.global.ratelimit.PublicLinkAuditor;
import com.mamoki.ieojuda.global.scan.MalwareScanner;
import com.mamoki.ieojuda.global.scan.ScanResult;
import com.mamoki.ieojuda.global.storage.EvidenceStorageClient;
import com.mamoki.ieojuda.global.storage.contract.StoredEvidence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// issue "증빙 격리·검사" - 클라이언트가 보낸 Content-Type이 아니라 매직바이트로 실제 형식을 판별하고,
// 악성코드 시그니처를 통과한 파일만 저장소에 올라가야 한다.
class EvidenceSubmitServiceTest {

    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final UUID CASE_ID = UUID.randomUUID();

    private ReleaseCaseRepository releaseCaseRepository;
    private EvidenceRepository evidenceRepository;
    private EvidenceStorageClient evidenceStorageClient;
    private PublicLinkAuditor publicLinkAuditor;
    private IdempotencyGuard idempotencyGuard;
    private MalwareScanner malwareScanner;
    private EvidenceOrphanCleanupService evidenceOrphanCleanupService;
    private SecurityTokenService securityTokenService;
    private EvidenceSubmitService evidenceSubmitService;

    private Confirmer confirmer;
    private Plan plan;
    private ReleaseCase releaseCase;

    @BeforeEach
    void setUp() {
        releaseCaseRepository = mock(ReleaseCaseRepository.class);
        evidenceRepository = mock(EvidenceRepository.class);
        evidenceStorageClient = mock(EvidenceStorageClient.class);
        publicLinkAuditor = mock(PublicLinkAuditor.class);
        idempotencyGuard = mock(IdempotencyGuard.class);
        malwareScanner = mock(MalwareScanner.class);
        evidenceOrphanCleanupService = mock(EvidenceOrphanCleanupService.class);
        securityTokenService = mock(SecurityTokenService.class);
        evidenceSubmitService = new EvidenceSubmitService(
                releaseCaseRepository, evidenceRepository, evidenceStorageClient,
                publicLinkAuditor, idempotencyGuard, malwareScanner, evidenceOrphanCleanupService, securityTokenService);

        confirmer = mock(Confirmer.class);
        when(confirmer.getAcceptanceStatus()).thenReturn(AcceptanceStatus.ACCEPTED);
        plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(PLAN_ID);
        when(confirmer.getPlan()).thenReturn(plan);

        releaseCase = mock(ReleaseCase.class);
        when(releaseCase.getCaseId()).thenReturn(CASE_ID);
        when(releaseCase.getStatus()).thenReturn(ReleaseCaseStatus.EVIDENCE_PENDING);
        when(releaseCase.getPlan()).thenReturn(plan);
        when(releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(PLAN_ID)).thenReturn(Optional.of(releaseCase));
        when(releaseCaseRepository.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(releaseCase));

        // UPLOAD_EVIDENCE 토큰은 이 확인자·이 사건에 바인딩된 것으로 취급한다
        SecurityToken uploadEvidenceToken = mock(SecurityToken.class);
        when(uploadEvidenceToken.getConfirmer()).thenReturn(confirmer);
        when(uploadEvidenceToken.getReleaseCase()).thenReturn(releaseCase);
        when(securityTokenService.resolve(anyString(), eq(SecurityTokenPurpose.UPLOAD_EVIDENCE)))
                .thenReturn(uploadEvidenceToken);

        when(evidenceRepository.countByReleaseCase_CaseId(CASE_ID)).thenReturn(0L);
        when(evidenceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        // registerCompensatingDelete 관련 테스트가 스레드로컬 동기화 상태를 남기지 않도록 정리
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void submit_whenContentTypeClaimsPdfButBytesAreUnrecognizedFormat_isRejectedAndNeverStored() {
        // 클라이언트가 보낸 Content-Type(application/pdf)은 신뢰하지 않는다 - 매직바이트가
        // 허용 목록 어디에도 매칭되지 않으면(EXE 헤더 등) 거부한다.
        byte[] exeBytes = {'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "proof.pdf", "application/pdf", exeBytes);

        assertThatThrownBy(() -> evidenceSubmitService.submit(CASE_ID, "token", file, EvidenceType.DEATH_CERTIFICATE, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVIDENCE_SUBMISSION_INVALID));
        verify(evidenceStorageClient, never()).store(any(), any());
        verify(malwareScanner, never()).scan(any());
    }

    @Test
    void submit_whenMalwareScannerRejects_isRejectedAndNeverStored() {
        byte[] pdfBytes = "%PDF-1.4 EICAR-LIKE".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file", "proof.pdf", "application/pdf", pdfBytes);
        when(malwareScanner.scan(any())).thenReturn(ScanResult.infected("EICAR_TEST_SIGNATURE_DETECTED"));

        assertThatThrownBy(() -> evidenceSubmitService.submit(CASE_ID, "token", file, EvidenceType.DEATH_CERTIFICATE, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVIDENCE_SUBMISSION_INVALID));
        verify(evidenceStorageClient, never()).store(any(), any());
    }

    @Test
    void submit_whenFileExceedsFiftyMegabytes_rejectsAsPayloadTooLarge() {
        MockMultipartFile file = mock(MockMultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("proof.pdf");
        when(file.getSize()).thenReturn(51L * 1024 * 1024);

        assertThatThrownBy(() -> evidenceSubmitService.submit(CASE_ID, "token", file, EvidenceType.DEATH_CERTIFICATE, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE));
        verify(evidenceStorageClient, never()).store(any(), any());
    }

    // issue #93 - 옛 25MB 제한이었다면 거부됐을 30MB 파일이 새 50MB 제한에서는 실제로 통과해야 한다
    @Test
    void submit_whenFileIsThirtyMegabytes_previouslyRejectedNowSucceedsUnderNewFiftyMegabyteLimit() throws Exception {
        byte[] pdfBytes = "%PDF-1.4\n1 0 obj << >> endobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = mock(MockMultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("proof.pdf");
        when(file.getSize()).thenReturn(30L * 1024 * 1024);
        when(file.getInputStream()).thenAnswer(invocation -> new java.io.ByteArrayInputStream(pdfBytes));
        when(malwareScanner.scan(any())).thenReturn(ScanResult.passed());
        when(evidenceStorageClient.store(eq(CASE_ID), any())).thenReturn(new StoredEvidence("evidence/10/uuid.pdf", pdfBytes.length));

        evidenceSubmitService.submit(CASE_ID, "token", file, EvidenceType.DEATH_CERTIFICATE, null);

        verify(evidenceStorageClient).store(eq(CASE_ID), any());
    }

    @Test
    void submit_whenPdfIsCleanAndValid_storesAndStartsEvidenceReview() {
        byte[] pdfBytes = "%PDF-1.4\n1 0 obj << >> endobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file", "proof.pdf", "application/pdf", pdfBytes);
        when(malwareScanner.scan(any())).thenReturn(ScanResult.passed());
        when(evidenceStorageClient.store(eq(CASE_ID), any())).thenReturn(new StoredEvidence("evidence/10/uuid.pdf", pdfBytes.length));

        evidenceSubmitService.submit(CASE_ID, "token", file, EvidenceType.DEATH_CERTIFICATE, null);

        verify(evidenceStorageClient).store(eq(CASE_ID), any());
        verify(releaseCase).startEvidenceReview();

        // issue #88 완료 조건 - 제출한 증빙 종류가 실제로 저장되어야 한다
        ArgumentCaptor<Evidence> captor = ArgumentCaptor.forClass(Evidence.class);
        verify(evidenceRepository).save(captor.capture());
        assertThat(captor.getValue().getEvidenceType()).isEqualTo(EvidenceType.DEATH_CERTIFICATE);
    }

    @Test
    void submit_whenReleaseCaseNotYetInEvidencePending_doesNotStartEvidenceReviewAgain() {
        when(releaseCase.getStatus()).thenReturn(ReleaseCaseStatus.EVIDENCE_REVIEWING);
        byte[] pdfBytes = "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file", "proof.pdf", "application/pdf", pdfBytes);
        when(malwareScanner.scan(any())).thenReturn(ScanResult.passed());
        when(evidenceStorageClient.store(eq(CASE_ID), any())).thenReturn(new StoredEvidence("evidence/10/uuid.pdf", pdfBytes.length));

        evidenceSubmitService.submit(CASE_ID, "token", file, EvidenceType.DEATH_CERTIFICATE, null);

        verify(releaseCase, never()).startEvidenceReview();
    }

    // issue #51 - DB 트랜잭션이 롤백된 뒤 보정 삭제(afterCompletion) 자체가 실패하면, 이전에는 로그만
    // 남기고 예외를 삼켰다. 이제는 EvidenceOrphanCleanupService에 재처리 대상으로 기록해야 한다.
    @Test
    void submit_whenTransactionRollsBackAndCompensatingDeleteFails_recordsOrphanForRetry() {
        byte[] pdfBytes = "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file", "proof.pdf", "application/pdf", pdfBytes);
        when(malwareScanner.scan(any())).thenReturn(ScanResult.passed());
        when(evidenceStorageClient.store(eq(CASE_ID), any()))
                .thenReturn(new StoredEvidence("evidence/10/uuid.pdf", pdfBytes.length));
        // 보정 삭제 자체가 실패하는 상황을 재현
        doThrow(new RuntimeException("S3 unreachable")).when(evidenceStorageClient).delete("evidence/10/uuid.pdf");

        TransactionSynchronizationManager.initSynchronization();
        try {
            evidenceSubmitService.submit(CASE_ID, "token", file, EvidenceType.DEATH_CERTIFICATE, null);
            // 실제 Spring 트랜잭션이 롤백된 뒤 afterCompletion을 호출하는 것과 동일하게 재현
            TransactionSynchronizationUtils.triggerAfterCompletion(
                    org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(evidenceOrphanCleanupService).recordOrphan(eq("evidence/10/uuid.pdf"), eq("RuntimeException"));
    }
}
