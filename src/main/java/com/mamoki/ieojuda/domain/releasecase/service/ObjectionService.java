package com.mamoki.ieojuda.domain.releasecase.service;

import java.util.UUID;

import com.mamoki.ieojuda.domain.confirmer.entity.DisputeContact;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.releasecase.dto.ObjectionRequest;
import com.mamoki.ieojuda.domain.releasecase.dto.ObjectionResponse;
import com.mamoki.ieojuda.domain.releasecase.entity.Objection;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.repository.ObjectionRepository;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityToken;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.ratelimit.PublicLinkAuditor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 명세서 "사후 인계" 화면 - "무언가 잘못됐다면?" 이의 제기 접수. 검증된 이의 제기 연락처만 접수할 수 있다 (로그인 불필요, RAISE_OBJECTION 토큰이 곧 인증)
// issue #41 - 이메일 검증에 쓰인 토큰과는 별개인 RAISE_OBJECTION 전용 토큰(사건 바인딩, 단일 사용)만 여기서 받는다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ObjectionService {

    private final ObjectionRepository objectionRepository;
    private final PublicLinkAuditor publicLinkAuditor;
    private final SecurityTokenService securityTokenService;

    @Transactional
    public ObjectionResponse raise(UUID caseId, ObjectionRequest request) {
        SecurityToken token = securityTokenService.resolve(request.token(), SecurityTokenPurpose.RAISE_OBJECTION);
        DisputeContact contact = token.getDisputeContact();

        if (!Boolean.TRUE.equals(contact.getIsVerified())) {
            publicLinkAuditor.recordStateFailure(ErrorCode.DISPUTE_CONTACT_NOT_VERIFIED);
            throw new CustomException(ErrorCode.DISPUTE_CONTACT_NOT_VERIFIED);
        }

        // URL의 caseId가 토큰 발급 시 묶인 사건과 실제로 같은지 검증 - 토큰은 유효하지만
        // 다른 사건의 caseId를 끼워 넣는 시도를 막는다.
        if (token.getReleaseCase() == null || !token.getReleaseCase().getCaseId().equals(caseId)) {
            publicLinkAuditor.recordStateFailure(ErrorCode.FORBIDDEN);
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        ReleaseCase releaseCase = token.getReleaseCase();
        Plan plan = contact.getPlan();

        Objection objection = objectionRepository.save(Objection.builder()
                .plan(plan)
                .releaseCase(releaseCase)
                .disputeContact(contact)
                .reason(request.reason())
                .build());

        releaseCase.raiseDispute();
        securityTokenService.consume(token);

        return ObjectionResponse.from(objection);
    }
}
