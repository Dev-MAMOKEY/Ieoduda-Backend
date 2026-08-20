package com.mamoki.ieojuda.domain.releasecase.service;

import java.util.EnumSet;
import java.util.UUID;

import com.mamoki.ieojuda.domain.releasecase.dto.PublicWaitingStatusResponse;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityToken;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 명세서 "사후 인계" 화면 - 경고 메일 링크로 접근한 작성자 본인 / 이의 제기 연락처가 로그인 없이 대기
// 상태를 조회한다. CaseCancellationService/ObjectionService와 같은 CANCEL_CASE·RAISE_OBJECTION
// 토큰을 쓰지만, 여기서는 조회만 하고 토큰을 소비하지 않는다 - 실제 취소/이의제기 시 다시 쓰여야 하므로.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicWaitingStatusService {

    private final SecurityTokenService securityTokenService;

    public PublicWaitingStatusResponse getStatus(UUID caseId, String plainToken) {
        SecurityToken token = securityTokenService.resolveAny(plainToken,
                EnumSet.of(SecurityTokenPurpose.CANCEL_CASE, SecurityTokenPurpose.RAISE_OBJECTION));

        // URL의 caseId가 토큰 발급 시 묶인 사건과 실제로 같은지 검증 - CaseCancellationService/ObjectionService와 동일 패턴
        if (token.getReleaseCase() == null || !token.getReleaseCase().getCaseId().equals(caseId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        ReleaseCase releaseCase = token.getReleaseCase();

        return PublicWaitingStatusResponse.of(releaseCase, token.getPurpose());
    }
}
