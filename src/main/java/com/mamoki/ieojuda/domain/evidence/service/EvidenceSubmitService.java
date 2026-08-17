package com.mamoki.ieojuda.domain.evidence.service;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.evidence.dto.EvidenceSubmitResponse;
import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCaseStatus;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.idempotency.service.IdempotencyGuard;
import com.mamoki.ieojuda.global.ratelimit.PublicLinkAuditor;
import com.mamoki.ieojuda.global.ratelimit.TokenLookupGuard;
import com.mamoki.ieojuda.global.scan.FileSignatureDetector;
import com.mamoki.ieojuda.global.scan.MalwareScanner;
import com.mamoki.ieojuda.global.scan.ScanResult;
import com.mamoki.ieojuda.global.storage.EvidenceStorageClient;
import com.mamoki.ieojuda.global.storage.IntegrityHasher;
import com.mamoki.ieojuda.global.storage.contract.EvidenceUpload;
import com.mamoki.ieojuda.global.storage.contract.StoredEvidence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.function.Supplier;

// 명세서 "공식 증빙 자료 제출" 화면 - 사망 신고가 확인된 후, 지정 확인자가 증빙 파일을 제출한다 (로그인 불필요, 초대 토큰이 곧 인증)
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvidenceSubmitService {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");
    private static final long MAX_FILE_SIZE = 25L * 1024 * 1024; // 25MB
    private static final long MAX_FILE_COUNT = 3;
    private static final int HEADER_LENGTH = 16;

    private final ConfirmerRepository confirmerRepository;
    private final ReleaseCaseRepository releaseCaseRepository;
    private final EvidenceRepository evidenceRepository;
    private final EvidenceStorageClient evidenceStorageClient;
    private final TokenLookupGuard tokenLookupGuard;
    private final PublicLinkAuditor publicLinkAuditor;
    private final IdempotencyGuard idempotencyGuard;
    private final MalwareScanner malwareScanner;
    private final EvidenceOrphanCleanupService evidenceOrphanCleanupService;

    @Transactional
    public EvidenceSubmitResponse submit(String plainToken, MultipartFile file, String idempotencyKey) {
        Confirmer confirmer = findByToken(plainToken);
        if (confirmer.getAcceptanceStatus() != AcceptanceStatus.ACCEPTED) {
            publicLinkAuditor.recordStateFailure(ErrorCode.FORBIDDEN);
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        idempotencyGuard.claim("evidence-submit", idempotencyKey);

        Plan plan = confirmer.getPlan();
        ReleaseCase releaseCase = releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(plan.getPlanId())
                .orElseThrow(() -> new CustomException(ErrorCode.RELEASE_CASE_NOT_FOUND));

        // "개수 확인 후 저장" 사이에 다른 요청이 끼어들지 못하도록, 이 트랜잭션이 끝날 때까지
        // 같은 사건에 대한 다른 증빙 제출을 사건 행 잠금으로 직렬화한다.
        releaseCase = releaseCaseRepository.findByIdForUpdate(releaseCase.getCaseId())
                .orElseThrow(() -> new CustomException(ErrorCode.RELEASE_CASE_NOT_FOUND));

        validateBasics(file);
        InspectionResult inspection = inspect(file);
        if (!inspection.accepted()) {
            publicLinkAuditor.recordStateFailure(ErrorCode.EVIDENCE_SUBMISSION_INVALID);
            log.warn("[Evidence Rejected] caseId={}, reason={}", releaseCase.getCaseId(), inspection.reason());
            throw new CustomException(ErrorCode.EVIDENCE_SUBMISSION_INVALID);
        }

        if (evidenceRepository.countByReleaseCase_CaseId(releaseCase.getCaseId()) >= MAX_FILE_COUNT) {
            throw new CustomException(ErrorCode.EVIDENCE_SUBMISSION_INVALID);
        }

        EvidenceUpload upload = new EvidenceUpload(
                file.getOriginalFilename(), inspection.mimeType(), openStream(file), file.getSize());
        StoredEvidence stored = evidenceStorageClient.store(releaseCase.getCaseId(), upload);
        registerCompensatingDelete(stored.storageKey());

        Evidence evidence = evidenceRepository.save(Evidence.builder()
                .confirmer(confirmer)
                .plan(plan)
                .releaseCase(releaseCase)
                .storageKey(stored.storageKey())
                .fileName(file.getOriginalFilename())
                .mimeType(inspection.mimeType())
                .integrityHash(inspection.integrityHash())
                .build());

        if (releaseCase.getStatus() == ReleaseCaseStatus.EVIDENCE_PENDING) {
            releaseCase.startEvidenceReview();
        }

        return EvidenceSubmitResponse.from(evidence);
    }

    private void validateBasics(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.EVIDENCE_SUBMISSION_INVALID);
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank() || originalFilename.length() > 255) {
            throw new CustomException(ErrorCode.EVIDENCE_SUBMISSION_INVALID);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new CustomException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
    }

    // 클라이언트가 보낸 Content-Type은 신뢰하지 않는다(위조 가능) - 매직바이트로 실제 형식을 판별하고,
    // 허용된 형식일 때만 같은 파일을 악성코드 시그니처로 검사한 뒤, 통과한 파일만 무결성 해시를 계산한다.
    private InspectionResult inspect(MultipartFile file) {
        String detectedMimeType = detectMimeType(file);
        if (detectedMimeType == null || !ALLOWED_MIME_TYPES.contains(detectedMimeType)) {
            return InspectionResult.rejected("UNRECOGNIZED_OR_DISALLOWED_FORMAT");
        }

        ScanResult scanResult = scan(file);
        if (!scanResult.clean()) {
            return InspectionResult.rejected(scanResult.detail());
        }

        return InspectionResult.accepted(detectedMimeType, hash(file));
    }

    private String detectMimeType(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = is.readNBytes(HEADER_LENGTH);
            return FileSignatureDetector.detect(header).orElse(null);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.EVIDENCE_SUBMISSION_INVALID);
        }
    }

    private ScanResult scan(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            return malwareScanner.scan(is);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.EVIDENCE_SUBMISSION_INVALID);
        }
    }

    private String hash(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            return IntegrityHasher.sha256Hex(is);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.EVIDENCE_SUBMISSION_INVALID);
        }
    }

    // S3 SDK가 전송 실패로 재시도할 때마다 새 스트림을 열 수 있도록 Supplier로 감싼다.
    private Supplier<InputStream> openStream(MultipartFile file) {
        return () -> {
            try {
                return file.getInputStream();
            } catch (IOException e) {
                throw new CustomException(ErrorCode.EVIDENCE_SUBMISSION_INVALID);
            }
        };
    }

    // ReleaseCase에는 낙관적 잠금(@Version)이 있고, 이 트랜잭션이 잡은 행 잠금은 admin 쪽 freeze·배정
    // 조작까지는 막지 못한다 - 커밋 시점에 낙관적 잠금 충돌이 나면 S3 PUT은 이미 끝난 뒤라 catch로
    // 잡을 수 없으므로, 트랜잭션 완료 이후 훅(커밋 실패 포함)에 보상 삭제를 등록해 고아 객체를 막는다.
    private void registerCompensatingDelete(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return;
                }
                try {
                    evidenceStorageClient.delete(storageKey);
                } catch (Exception e) {
                    log.error("[Evidence Orphan Cleanup Failed] storageKey={}, cause={}", storageKey, e.getMessage(), e);
                    evidenceOrphanCleanupService.recordOrphan(storageKey, e.getClass().getSimpleName());
                }
            }
        });
    }

    private Confirmer findByToken(String plainToken) {
        return tokenLookupGuard.resolve(plainToken,
                () -> confirmerRepository.findByInviteToken(TokenProvider.hashToken(plainToken)));
    }

    private record InspectionResult(boolean accepted, String mimeType, String integrityHash, String reason) {
        static InspectionResult accepted(String mimeType, String integrityHash) {
            return new InspectionResult(true, mimeType, integrityHash, null);
        }

        static InspectionResult rejected(String reason) {
            return new InspectionResult(false, null, null, reason);
        }
    }
}
