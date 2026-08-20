package com.mamoki.ieojuda.domain.releasecase.service;

import java.util.UUID;

import com.mamoki.ieojuda.domain.account.entity.AdminPermission;
import com.mamoki.ieojuda.domain.releasecase.dto.ReleaseCaseDetailResponse;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.security.PermissionGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 관리자 화면 - caseId로 임의의 사건을 상세 조회 (소유자 무관, ReleaseStatusService.findOwnedCase()와 달리 소유권 검사 없음)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReleaseCaseDetailService {

    private final ReleaseCaseRepository releaseCaseRepository;
    private final PermissionGuard permissionGuard;

    public ReleaseCaseDetailResponse getDetail(UUID adminUserId, UUID caseId) {
        permissionGuard.require(adminUserId, AdminPermission.CASE_SUPERVISE);
        ReleaseCase releaseCase = releaseCaseRepository.findById(caseId)
                .orElseThrow(() -> new CustomException(ErrorCode.RELEASE_CASE_NOT_FOUND));
        return ReleaseCaseDetailResponse.of(releaseCase);
    }
}
