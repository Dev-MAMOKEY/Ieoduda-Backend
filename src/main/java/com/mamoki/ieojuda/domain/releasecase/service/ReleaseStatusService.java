package com.mamoki.ieojuda.domain.releasecase.service;

import java.util.UUID;

import com.mamoki.ieojuda.domain.releasecase.dto.ReleaseStatusResponse;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 명세서 "사후 인계" 화면 - 작성자 본인이 자기 계획의 대기 상태를 조회하고, 필요하면 직접 취소한다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReleaseStatusService {

    private final ReleaseCaseRepository releaseCaseRepository;

    public ReleaseStatusResponse getStatus(UUID userId, UUID caseId) {
        ReleaseCase releaseCase = findOwnedCase(userId, caseId);
        return ReleaseStatusResponse.of(releaseCase);
    }

    // "살아계신가요? 지금 멈출 수 있어요" - 본인 확인 후 절차 전체를 즉시 취소
    @Transactional
    public ReleaseStatusResponse cancel(UUID userId, UUID caseId) {
        ReleaseCase releaseCase = findOwnedCase(userId, caseId);
        releaseCase.cancel();
        return ReleaseStatusResponse.of(releaseCase);
    }

    private ReleaseCase findOwnedCase(UUID userId, UUID caseId) {
        ReleaseCase releaseCase = releaseCaseRepository.findById(caseId)
                .orElseThrow(() -> new CustomException(ErrorCode.RELEASE_CASE_NOT_FOUND));
        if (!releaseCase.getPlan().getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        return releaseCase;
    }
}
