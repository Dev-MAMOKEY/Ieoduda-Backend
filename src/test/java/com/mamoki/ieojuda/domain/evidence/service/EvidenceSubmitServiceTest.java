package com.mamoki.ieojuda.domain.evidence.service;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCaseStatus;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.idempotency.service.IdempotencyGuard;
import com.mamoki.ieojuda.global.ratelimit.PublicLinkAuditor;
import com.mamoki.ieojuda.global.ratelimit.TokenLookupGuard;
import com.mamoki.ieojuda.global.scan.MalwareScanner;
import com.mamoki.ieojuda.global.scan.ScanResult;
import com.mamoki.ieojuda.global.storage.EvidenceStorageClient;
import com.mamoki.ieojuda.global.storage.contract.StoredEvidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// issue "증빙 격리·검사" - 클라이언트가 보낸 Content-Type이 아니라 매직바이트로 실제 형식을 판별하고,
// 악성코드 시그니처를 통과한 파일만 저장소에 올라가야 한다.
class EvidenceSubmitServiceTest {

    private ConfirmerRepository confirmerRepository;
    private ReleaseCaseRepository releaseCaseRepository;
    private EvidenceRepository evidenceRepository;
    private EvidenceStorageClient evidenceStorageClient;
    private TokenLookupGuard tokenLookupGuard;
    private PublicLinkAuditor publicLinkAuditor;
    private IdempotencyGuard idempotencyGuard;
    private MalwareScanner malwareScanner;
    private EvidenceSubmitService evidenceSubmitService;

    private Confirmer confirmer;
    private Plan plan;
    private ReleaseCase releaseCase;

    @BeforeEach
    void setUp() {
        confirmerRepository = mock(ConfirmerRepository.class);
        releaseCaseRepository = mock(ReleaseCaseRepository.class);
        evidenceRepository = mock(EvidenceRepository.class);
        evidenceStorageClient = mock(EvidenceStorageClient.class);
        tokenLookupGuard = mock(TokenLookupGuard.class);
        publicLinkAuditor = mock(PublicLinkAuditor.class);
        idempotencyGuard = mock(IdempotencyGuard.class);
        malwareScanner = mock(MalwareScanner.class);
        evidenceSubmitService = new EvidenceSubmitService(
                confirmerRepository, releaseCaseRepository, evidenceRepository, evidenceStorageClient,
                tokenLookupGuard, publicLinkAuditor, idempotencyGuard, malwareScanner);

        // TokenLookupGuard는 실제 구현처럼 supplier를 그대로 실행해 confirmerRepository 목 설정이
        // 기존과 동일하게 동작하도록 위임한다.
        when(tokenLookupGuard.resolve(anyString(), any())).thenAnswer(invocation -> {
            Supplier<Optional<?>> lookup = invocation.getArgument(1);
            return lookup.get().orElseThrow(() -> new CustomException(ErrorCode.TOKEN_INVALID));
        });

        confirmer = mock(Confirmer.class);
        when(confirmer.getAcceptanceStatus()).thenReturn(AcceptanceStatus.ACCEPTED);
        plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(1L);
        when(confirmer.getPlan()).thenReturn(plan);
        when(confirmerRepository.findByInviteToken(any())).thenReturn(Optional.of(confirmer));

        releaseCase = mock(ReleaseCase.class);
        when(releaseCase.getCaseId()).thenReturn(10L);
        when(releaseCase.getStatus()).thenReturn(ReleaseCaseStatus.EVIDENCE_PENDING);
        when(releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(1L)).thenReturn(Optional.of(releaseCase));
        when(releaseCaseRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(releaseCase));

        when(evidenceRepository.countByReleaseCase_CaseId(10L)).thenReturn(0L);
        when(evidenceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void submit_whenContentTypeClaimsPdfButBytesAreUnrecognizedFormat_isRejectedAndNeverStored() {
        // 클라이언트가 보낸 Content-Type(application/pdf)은 신뢰하지 않는다 - 매직바이트가
        // 허용 목록 어디에도 매칭되지 않으면(EXE 헤더 등) 거부한다.
        byte[] exeBytes = {'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "proof.pdf", "application/pdf", exeBytes);

        assertThatThrownBy(() -> evidenceSubmitService.submit("token", file, null))
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

        assertThatThrownBy(() -> evidenceSubmitService.submit("token", file, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVIDENCE_SUBMISSION_INVALID));
        verify(evidenceStorageClient, never()).store(any(), any());
    }

    @Test
    void submit_whenFileExceedsTwentyFiveMegabytes_rejectsAsPayloadTooLarge() {
        MockMultipartFile file = mock(MockMultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("proof.pdf");
        when(file.getSize()).thenReturn(26L * 1024 * 1024);

        assertThatThrownBy(() -> evidenceSubmitService.submit("token", file, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE));
        verify(evidenceStorageClient, never()).store(any(), any());
    }

    @Test
    void submit_whenPdfIsCleanAndValid_storesAndStartsEvidenceReview() {
        byte[] pdfBytes = "%PDF-1.4\n1 0 obj << >> endobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file", "proof.pdf", "application/pdf", pdfBytes);
        when(malwareScanner.scan(any())).thenReturn(ScanResult.passed());
        when(evidenceStorageClient.store(eq(10L), any())).thenReturn(new StoredEvidence("evidence/10/uuid.pdf", pdfBytes.length));

        evidenceSubmitService.submit("token", file, null);

        verify(evidenceStorageClient).store(eq(10L), any());
        verify(releaseCase).startEvidenceReview();
    }

    @Test
    void submit_whenReleaseCaseNotYetInEvidencePending_doesNotStartEvidenceReviewAgain() {
        when(releaseCase.getStatus()).thenReturn(ReleaseCaseStatus.EVIDENCE_REVIEWING);
        byte[] pdfBytes = "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file", "proof.pdf", "application/pdf", pdfBytes);
        when(malwareScanner.scan(any())).thenReturn(ScanResult.passed());
        when(evidenceStorageClient.store(eq(10L), any())).thenReturn(new StoredEvidence("evidence/10/uuid.pdf", pdfBytes.length));

        evidenceSubmitService.submit("token", file, null);

        verify(releaseCase, never()).startEvidenceReview();
    }
}
