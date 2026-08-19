package com.mamoki.ieojuda.domain.confirmer.service;

import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerDecisionRequest;
import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerDecisionResponse;
import com.mamoki.ieojuda.domain.confirmer.dto.ConfirmerInviteResponse;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityToken;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxService;
import com.mamoki.ieojuda.global.email.template.EmailBuilder;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.ratelimit.PublicLinkAuditor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

// 명세서 "지정확인자 수락 이메일/화면" - 확인자가 초대 링크로 진입해 역할을 확인하고 수락/거절 (로그인 불필요, 토큰이 곧 인증)
// issue #41 - 수락/거절은 ACCEPT_ROLE 목적 토큰으로만 처리한다. 수락이 완료되는 순간, 이후 사망 신고에
// 쓸 별도의 REPORT_DEATH 토큰을 함께 발급해 응답에 담아 돌려준다(노션 "자신의 확인자 링크" 진입 경로 대응) -
// 사망 신고가 ACCEPT_ROLE 토큰을 영구 접근키로 재사용하지 않도록 하기 위함이다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConfirmerInviteService {

    // 사망 신고는 수락 시점으로부터 수십 년 뒤에 이뤄질 수도 있어, 다른 토큰과 달리 사실상 무기한 수준으로 길게 둔다
    private static final long REPORT_DEATH_TOKEN_TTL_YEARS = 100;

    private final AppProperties appProperties;
    private final PublicLinkAuditor publicLinkAuditor;
    private final SecurityTokenService securityTokenService;
    private final EmailOutboxService emailOutboxService;

    // 초대 조회 - 만료·사용·폐기·목적 불일치 링크는 차단
    @Transactional
    public ConfirmerInviteResponse getInvite(String plainToken) {
        SecurityToken token = resolveAcceptToken(plainToken);
        Confirmer confirmer = token.getConfirmer();

        String ownerName = confirmer.getPlan().getUser().getName();
        return ConfirmerInviteResponse.of(confirmer, ownerName, token.getExpiresAt(), appProperties.getContactEmail());
    }

    // 역할 수락 - 수락과 동시에 사망 신고 전용 토큰을 새로 발급한다
    @Transactional
    public ConfirmerDecisionResponse accept(String plainToken, ConfirmerDecisionRequest request) {
        SecurityToken token = resolveAcceptToken(plainToken);
        Confirmer confirmer = token.getConfirmer();
        checkPending(confirmer);

        confirmer.accept(inquiryOf(request));
        securityTokenService.consume(token);

        LocalDateTime reportDeathExpiresAt = LocalDateTime.now().plusYears(REPORT_DEATH_TOKEN_TTL_YEARS);
        String reportDeathToken = securityTokenService.issueForConfirmer(
                SecurityTokenPurpose.REPORT_DEATH, confirmer, null, reportDeathExpiresAt);
        sendDeathReportEmail(confirmer, reportDeathToken, reportDeathExpiresAt);

        return ConfirmerDecisionResponse.of(confirmer, reportDeathToken);
    }

    // 수락 완료 즉시 "사망 신고" 화면 링크를 이메일로 보내, 이후 응답을 잃어버려도 이 이메일로 돌아올 수 있게 한다
    private void sendDeathReportEmail(Confirmer confirmer, String plainToken, LocalDateTime expiresAt) {
        String secureLink = appProperties.getBaseUrl() + "/death-report?token=" + plainToken;
        EmailContent content = EmailBuilder.build(
                "지정 확인자",
                "사망 사실을 신고해 주세요.",
                expiresAt.atZone(ZoneId.systemDefault()),
                secureLink,
                appProperties.getContactEmail()
        );
        emailOutboxService.enqueue(confirmer.getPlan(), null, EmailType.DEATH_REPORT_REQUEST, confirmer.getEmail(), content);
    }

    // 역할 거절
    @Transactional
    public ConfirmerDecisionResponse decline(String plainToken, ConfirmerDecisionRequest request) {
        SecurityToken token = resolveAcceptToken(plainToken);
        Confirmer confirmer = token.getConfirmer();
        checkPending(confirmer);

        confirmer.decline(inquiryOf(request));
        securityTokenService.consume(token);

        return ConfirmerDecisionResponse.of(confirmer, null);
    }

    // 문의 사항은 선택 입력이라 요청 바디 자체가 없을 수 있다
    private String inquiryOf(ConfirmerDecisionRequest request) {
        return request == null ? null : request.inquiry();
    }

    private SecurityToken resolveAcceptToken(String plainToken) {
        return securityTokenService.resolve(plainToken, SecurityTokenPurpose.ACCEPT_ROLE);
    }

    // 수락/거절은 대기 중(PENDING) 상태에서만 가능 - 이미 처리된 링크의 재사용을 차단
    private void checkPending(Confirmer confirmer) {
        if (confirmer.getAcceptanceStatus() != AcceptanceStatus.PENDING) {
            publicLinkAuditor.recordStateFailure(ErrorCode.ACCESS_LINK_ALREADY_USED);
            throw new CustomException(ErrorCode.ACCESS_LINK_ALREADY_USED);
        }
    }
}
