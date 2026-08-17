package com.mamoki.ieojuda.domain.releasecase.service;

import java.util.UUID;

import com.mamoki.ieojuda.domain.confirmer.entity.DisputeContact;
import com.mamoki.ieojuda.domain.confirmer.repository.DisputeContactRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.releasecase.dto.ObjectionRequest;
import com.mamoki.ieojuda.domain.releasecase.dto.ObjectionResponse;
import com.mamoki.ieojuda.domain.releasecase.entity.Objection;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.repository.ObjectionRepository;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.ratelimit.PublicLinkAuditor;
import com.mamoki.ieojuda.global.ratelimit.TokenLookupGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 명세서 "사후 인계" 화면 - "무언가 잘못됐다면?" 이의 제기 접수. 검증된 이의 제기 연락처만 접수할 수 있다 (로그인 불필요, 초대 토큰이 곧 인증)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ObjectionService {

    private final DisputeContactRepository disputeContactRepository;
    private final ReleaseCaseRepository releaseCaseRepository;
    private final ObjectionRepository objectionRepository;
    private final TokenLookupGuard tokenLookupGuard;
    private final PublicLinkAuditor publicLinkAuditor;

    // 이메일 검증까지 마친 연락처는, 이후 언제든 이 토큰으로 다시 접근해 이의를 제기할 수 있는 개인 접근키를 겸한다
    // (검증 자체에 쓰인 만료시각은 "검증 대기" 창구용이라 여기서는 별도로 검사하지 않는다)
    @Transactional
    public ObjectionResponse raise(UUID caseId, ObjectionRequest request) {
        DisputeContact contact = tokenLookupGuard.resolve(request.token(),
                () -> disputeContactRepository.findByInviteToken(TokenProvider.hashToken(request.token())));

        if (!Boolean.TRUE.equals(contact.getIsVerified())) {
            publicLinkAuditor.recordStateFailure(ErrorCode.DISPUTE_CONTACT_NOT_VERIFIED);
            throw new CustomException(ErrorCode.DISPUTE_CONTACT_NOT_VERIFIED);
        }

        ReleaseCase releaseCase = releaseCaseRepository.findById(caseId)
                .orElseThrow(() -> new CustomException(ErrorCode.RELEASE_CASE_NOT_FOUND));

        Plan plan = contact.getPlan();
        // URL의 caseId가 토큰이 가리키는 이의 제기 연락처의 사건과 실제로 같은지 검증 - 토큰은 유효하지만
        // 다른 사건의 caseId를 끼워 넣는 시도를 막는다.
        if (!releaseCase.getPlan().getPlanId().equals(plan.getPlanId())) {
            publicLinkAuditor.recordStateFailure(ErrorCode.FORBIDDEN);
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        Objection objection = objectionRepository.save(Objection.builder()
                .plan(plan)
                .releaseCase(releaseCase)
                .disputeContact(contact)
                .reason(request.reason())
                .build());

        releaseCase.raiseDispute();

        return ObjectionResponse.from(objection);
    }
}
