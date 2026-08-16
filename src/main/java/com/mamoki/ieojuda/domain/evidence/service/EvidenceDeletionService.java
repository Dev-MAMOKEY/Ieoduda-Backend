package com.mamoki.ieojuda.domain.evidence.service;

import com.mamoki.ieojuda.domain.account.entity.AdminPermission;
import com.mamoki.ieojuda.domain.evidence.dto.EvidenceDeletionStatusResponse;
import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.security.PermissionGuard;
import com.mamoki.ieojuda.global.storage.EvidenceStorageClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 명세서 "증빙 삭제 감사" 화면 - 서비스 운영자·외부 파트너 공용
// issue #59 - EVIDENCE_DELETE_RETRY 세부 권한 검사
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvidenceDeletionService {

    private final EvidenceRepository evidenceRepository;
    private final EvidenceStorageClient evidenceStorageClient;
    private final PermissionGuard permissionGuard;

    public EvidenceDeletionStatusResponse getStatus(Long userId, Long evidenceId) {
        permissionGuard.require(userId, AdminPermission.EVIDENCE_DELETE_RETRY);
        return EvidenceDeletionStatusResponse.from(findEvidence(evidenceId));
    }

    // "재처리 요청하기" - 자동 삭제가 실패한 증빙을 다시 삭제 시도
    @Transactional
    public EvidenceDeletionStatusResponse retryDeletion(Long userId, Long evidenceId) {
        permissionGuard.require(userId, AdminPermission.EVIDENCE_DELETE_RETRY);
        Evidence evidence = findEvidence(evidenceId);

        if (evidence.getDeletedAt() != null) {
            return EvidenceDeletionStatusResponse.from(evidence); // 이미 삭제됨 - 그대로 성공 응답
        }

        try {
            evidenceStorageClient.delete(evidence.getStorageKey());
            evidence.markDeleted();
        } catch (CustomException e) {
            evidence.markDeleteFailed(e.getErrorCode().getMessage());
        }

        return EvidenceDeletionStatusResponse.from(evidence);
    }

    private Evidence findEvidence(Long evidenceId) {
        return evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new CustomException(ErrorCode.EVIDENCE_NOT_FOUND));
    }
}
