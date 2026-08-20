package com.mamoki.ieojuda.domain.releasecase.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.confirmer.entity.DisputeContact;
import com.mamoki.ieojuda.domain.confirmer.repository.DisputeContactRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.outbox.CriticalEmailSender;
import com.mamoki.ieojuda.global.email.template.EmailBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 이 작업의 핵심 - "공개 절차가 시작되기 전에 취소·이의 제기 경고가 실제로 전달된다"를 보장하기 위한
// 게이트. EmailOutbox(완전 비동기)와 달리 여기 두 메서드는 CriticalEmailSender로 발송 성공을 그 자리에서
// 확인한 뒤에만 사건 상태 전이를 수행한다 - 실패하면 전이를 하지 않고 releaseCase.freeze()로 진행을
// 멈춘다("이메일 발송 감사" 화면의 동결 메커니즘 재사용). 두 트리거(사건 생성, 증빙 승인)와 운영자
// 재시도 액션이 이 서비스를 공유한다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReleaseCaseWarningService {

    private static final int DEFAULT_WAITING_DAYS = 7;
    // "단기" 취소 토큰 - DeathReportService.EVIDENCE_TOKEN_TTL_DAYS와 동일 수준. appProperties의
    // activeTokenTtlDays(10년, 장기 보안 토큰용)를 재사용하면 사실상 무기한이 되어 "단기 토큰 발급"이라는
    // 완료 조건과 어긋나므로 별도 상수로 둔다.
    private static final long CANCEL_TOKEN_TTL_DAYS = 30;

    private final DisputeContactRepository disputeContactRepository;
    private final SecurityTokenService securityTokenService;
    private final CriticalEmailSender criticalEmailSender;
    private final AppProperties appProperties;

    // 사건 생성 직후 - 작성자(본인 경고 이메일, 이미 검증됨)에게 취소 링크를 보낸다.
    // 실패하면 사건을 동결해 운영 검토로 넘긴다(사건 자체는 이미 만들어져 있어야 하므로 생성을 되돌리지 않는다).
    @Transactional
    public boolean sendAuthorCancelWarningOrFreeze(ReleaseCase releaseCase) {
        Plan plan = releaseCase.getPlan();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(CANCEL_TOKEN_TTL_DAYS);
        String plainToken = securityTokenService.issueForCase(SecurityTokenPurpose.CANCEL_CASE, releaseCase, expiresAt);

        String secureLink = appProperties.getBaseUrl() + "/waiting?caseId=" + releaseCase.getCaseId() + "&token=" + plainToken;
        EmailContent content = EmailBuilder.build(
                "사후 절차 시작 및 취소 안내",
                "본인이 아니거나 절차를 멈추고 싶다면 링크를 눌러 즉시 취소해 주세요.",
                expiresAt.atZone(ZoneId.systemDefault()),
                secureLink,
                appProperties.getContactEmail()
        );

        boolean sent = criticalEmailSender.sendOrFail(plan, EmailType.CASE_CANCEL_WARNING, plan.getSelfWarningEmail(), content);
        if (!sent) {
            releaseCase.freeze();
        }
        return sent;
    }

    // 증빙이 전부 승인된 시점 - 검증된 이의 제기 연락처 전원에게 이의 제기 링크를 보낸 뒤에만 WAITING으로
    // 전이한다. 하나라도 발송에 실패하면(나머지 연락처에도 계속 시도는 하되) 전이를 하지 않고 동결한다.
    // waitingEndsAt을 먼저 확정해 WAITING에 들어간 뒤 발송 실패를 알면, 나중에 동결이 풀렸을 때 이미 지난
    // waitingEndsAt 때문에 경고가 도착하자마자 발송 단계로 넘어가는 문제가 생기므로 순서를 반드시 지킨다.
    @Transactional
    public boolean sendDisputeWarningsAndStartWaiting(ReleaseCase releaseCase, Integer waitingDays) {
        LocalDateTime intendedWaitingEndsAt = LocalDateTime.now().plusDays(waitingDays != null ? waitingDays : DEFAULT_WAITING_DAYS);

        List<DisputeContact> contacts = disputeContactRepository.findByPlan_PlanId(releaseCase.getPlan().getPlanId());
        boolean allSent = true;
        for (DisputeContact contact : contacts) {
            if (!Boolean.TRUE.equals(contact.getIsVerified())) {
                continue;
            }
            if (!sendObjectionWindowWarning(releaseCase, contact, intendedWaitingEndsAt)) {
                allSent = false;
            }
        }

        if (allSent) {
            releaseCase.approveEvidenceAndStartWaiting(waitingDays);
            securityTokenService.revokeAllForCase(releaseCase, SecurityTokenPurpose.UPLOAD_EVIDENCE);
        } else {
            releaseCase.freeze();
        }
        return allSent;
    }

    private boolean sendObjectionWindowWarning(ReleaseCase releaseCase, DisputeContact contact, LocalDateTime expiresAt) {
        String plainToken = securityTokenService.issueForDisputeContact(
                SecurityTokenPurpose.RAISE_OBJECTION, contact, releaseCase, expiresAt);

        String secureLink = appProperties.getBaseUrl() + "/waiting?caseId=" + releaseCase.getCaseId() + "&token=" + plainToken;
        EmailContent content = EmailBuilder.build(
                "이의 제기 가능 기간 안내",
                "잘못된 점이 있다면 링크로 들어가 이의를 제기해 주세요.",
                expiresAt.atZone(ZoneId.systemDefault()),
                secureLink,
                appProperties.getContactEmail()
        );
        return criticalEmailSender.sendOrFail(contact.getPlan(), EmailType.OBJECTION_WINDOW_NOTICE, contact.getEmail(), content);
    }
}
