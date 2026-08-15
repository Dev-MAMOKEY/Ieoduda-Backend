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
import com.mamoki.ieojuda.global.storage.EvidenceStorageClient;
import com.mamoki.ieojuda.global.storage.contract.EvidenceUpload;
import com.mamoki.ieojuda.global.storage.contract.StoredEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

// 명세서 "공식 증빙 자료 제출" 화면 - 사망 신고가 확인된 후, 지정 확인자가 증빙 파일을 제출한다 (로그인 불필요, 초대 토큰이 곧 인증)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvidenceSubmitService {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10MB
    private static final long MAX_FILE_COUNT = 3;

    private final ConfirmerRepository confirmerRepository;
    private final ReleaseCaseRepository releaseCaseRepository;
    private final EvidenceRepository evidenceRepository;
    private final EvidenceStorageClient evidenceStorageClient;

    @Transactional
    public EvidenceSubmitResponse submit(String plainToken, MultipartFile file) {
        Confirmer confirmer = findByToken(plainToken);
        if (confirmer.getAcceptanceStatus() != AcceptanceStatus.ACCEPTED) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        Plan plan = confirmer.getPlan();
        ReleaseCase releaseCase = releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(plan.getPlanId())
                .orElseThrow(() -> new CustomException(ErrorCode.RELEASE_CASE_NOT_FOUND));

        validateFile(file);
        if (evidenceRepository.countByReleaseCase_CaseId(releaseCase.getCaseId()) >= MAX_FILE_COUNT) {
            throw new CustomException(ErrorCode.EVIDENCE_SUBMISSION_INVALID);
        }

        EvidenceUpload upload = new EvidenceUpload(file.getOriginalFilename(), file.getContentType(), readBytes(file));
        StoredEvidence stored = evidenceStorageClient.store(releaseCase.getCaseId(), upload);

        Evidence evidence = evidenceRepository.save(Evidence.builder()
                .confirmer(confirmer)
                .plan(plan)
                .releaseCase(releaseCase)
                .storageKey(stored.storageKey())
                .fileName(file.getOriginalFilename())
                .mimeType(file.getContentType())
                .integrityHash(stored.integrityHash())
                .build());

        if (releaseCase.getStatus() == ReleaseCaseStatus.EVIDENCE_PENDING) {
            releaseCase.startEvidenceReview();
        }

        return EvidenceSubmitResponse.from(evidence);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.EVIDENCE_SUBMISSION_INVALID);
        }
        if (!ALLOWED_MIME_TYPES.contains(file.getContentType())) {
            throw new CustomException(ErrorCode.EVIDENCE_SUBMISSION_INVALID);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new CustomException(ErrorCode.EVIDENCE_SUBMISSION_INVALID);
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new CustomException(ErrorCode.EVIDENCE_SUBMISSION_INVALID);
        }
    }

    private Confirmer findByToken(String plainToken) {
        return confirmerRepository.findByInviteToken(TokenProvider.hashToken(plainToken))
                .orElseThrow(() -> new CustomException(ErrorCode.TOKEN_INVALID));
    }
}
