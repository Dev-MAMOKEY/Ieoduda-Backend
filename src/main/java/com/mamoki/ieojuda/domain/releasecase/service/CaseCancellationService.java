package com.mamoki.ieojuda.domain.releasecase.service;

import java.util.UUID;

import com.mamoki.ieojuda.domain.releasecase.dto.ReleaseStatusResponse;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityToken;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 명세서 "사후 인계" 화면 - 사건 생성 시 발송된 경고 메일의 취소 링크로 접수 (로그인 불필요, CANCEL_CASE 토큰이 곧 인증)
// ReleaseStatusService.cancel()(로그인 필요)과 같은 취소 동작을 수행하되, 인증 수단만 다르다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CaseCancellationService {

    private final SecurityTokenService securityTokenService;

    @Transactional
    public ReleaseStatusResponse cancel(UUID caseId, String plainToken) {
        SecurityToken token = securityTokenService.resolve(plainToken, SecurityTokenPurpose.CANCEL_CASE);

        // URL의 caseId가 토큰 발급 시 묶인 사건과 실제로 같은지 검증 - ObjectionService.raise()와 동일 패턴
        if (token.getReleaseCase() == null || !token.getReleaseCase().getCaseId().equals(caseId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        ReleaseCase releaseCase = token.getReleaseCase();

        releaseCase.cancel();
        securityTokenService.consume(token);
        // issue #41 - 사건 종료(취소) 시 이 사건에 묶인 미사용 토큰을 전부 폐기한다 (ReleaseStatusService.cancel()과 동일)
        securityTokenService.revokeAllForCase(releaseCase, SecurityTokenPurpose.UPLOAD_EVIDENCE);
        securityTokenService.revokeAllForCase(releaseCase, SecurityTokenPurpose.RAISE_OBJECTION);

        return ReleaseStatusResponse.of(releaseCase);
    }
}
