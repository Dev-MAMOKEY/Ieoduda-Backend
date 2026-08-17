package com.mamoki.ieojuda.domain.audit.service;

import com.mamoki.ieojuda.domain.account.entity.AdminPermission;
import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.audit.dto.EmailDeliveryResponse;
import com.mamoki.ieojuda.domain.audit.entity.AdminActionType;
import com.mamoki.ieojuda.domain.audit.entity.EmailLog;
import com.mamoki.ieojuda.domain.audit.repository.EmailLogRepository;
import com.mamoki.ieojuda.domain.confirmer.entity.ReportStatus;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.evidence.entity.EvidenceReviewStatus;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCaseStatus;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.releasecase.service.ReleaseCaseWarningService;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxService;
import com.mamoki.ieojuda.global.email.template.EmailBuilder;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.security.PermissionGuard;
import com.mamoki.ieojuda.global.security.ReauthGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.List;

// 명세서 "이메일 발송 감사" 화면 - 서비스 운영자가 발송·반송·열람 이력을 조회하고 재시도/동결만 수행 (본문·패키지 내용은 접근 불가)
// issue #59 - CASE_SUPERVISE 세부 권한 검사, 사건 동결은 재인증 필요 + 감사 기록.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailAuditService {

    private final ReleaseCaseRepository releaseCaseRepository;
    private final EmailLogRepository emailLogRepository;
    private final EmailOutboxService emailOutboxService;
    private final AppProperties appProperties;
    private final PermissionGuard permissionGuard;
    private final ReauthGuard reauthGuard;
    private final AdminActionAuditService adminActionAuditService;
    private final SecurityTokenService securityTokenService;
    private final ReleaseCaseWarningService releaseCaseWarningService;
    private final EvidenceRepository evidenceRepository;
    private final ConfirmerRepository confirmerRepository;

    public List<EmailDeliveryResponse> getDeliveries(UUID userId, UUID caseId) {
        permissionGuard.require(userId, AdminPermission.CASE_SUPERVISE);
        ReleaseCase releaseCase = findCase(caseId);
        return emailLogRepository.findByPlan_PlanIdOrderByRequestedAtDesc(releaseCase.getPlan().getPlanId()).stream()
                .map(EmailDeliveryResponse::from)
                .toList();
    }

    // "재시도 정책 실행하기" - 이 발송 건과 연결된 담당자에게 새 초대 링크를 발급해 재발송
    @Transactional
    public EmailDeliveryResponse retry(UUID userId, UUID caseId, UUID logId) {
        permissionGuard.require(userId, AdminPermission.CASE_SUPERVISE);
        ReleaseCase releaseCase = findCase(caseId);
        if (Boolean.TRUE.equals(releaseCase.getFrozen())) {
            throw new CustomException(ErrorCode.RELEASE_CASE_FROZEN);
        }

        EmailLog log = findLog(releaseCase, logId);
        HandoverStage stage = log.getHandoverStage();
        if (stage == null) {
            // 재발송할 담당자(단계)가 연결 안 된 로그(예: 확인자·이의제기 연락처 발송)는 이 API로 재시도할 수 없음
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        Recipient recipient = stage.getRecipient();
        securityTokenService.revokeAllForRecipient(recipient, SecurityTokenPurpose.ACCEPT_ROLE);
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(appProperties.getInviteTokenTtlHours());
        String plainToken = securityTokenService.issueForRecipient(SecurityTokenPurpose.ACCEPT_ROLE, recipient, expiresAt);

        String secureLink = appProperties.getBaseUrl() + "/recipient-acceptances/" + plainToken;
        EmailContent content = EmailBuilder.build(
                "사후 인계 안내",
                "역할 수락 여부를 다시 확인해 주세요.",
                expiresAt.atZone(ZoneId.systemDefault()),
                secureLink,
                appProperties.getContactEmail()
        );
        emailOutboxService.enqueueRetry(log, content);

        return EmailDeliveryResponse.from(log);
    }

    // "사건 동결하기" - 이후 발송·단계 전환을 전부 막음. 되돌리기 까다로운 고위험 조작이라
    // 비밀번호 재확인(reauth) 없이는 실행하지 않고, 성공/실패 여부를 감사 로그에 남긴다.
    @Transactional
    public void freeze(UUID userId, UUID caseId, String password) {
        User actor = permissionGuard.require(userId, AdminPermission.CASE_SUPERVISE);
        try {
            reauthGuard.verify(actor, password);
        } catch (CustomException e) {
            adminActionAuditService.record(actor, AdminActionType.CASE_FREEZE, caseId, false, "재인증 실패");
            throw e;
        }

        findCase(caseId).freeze();
        adminActionAuditService.record(actor, AdminActionType.CASE_FREEZE, caseId, true, null);
    }

    // "사건 동결 해제하기" - freeze()와 대칭되는 순수 플래그 해제. 동결 사유(발송 실패로 인한 자동 동결이든,
    // 운영자가 임의로 건 동결이든)와 무관하게 항상 사용할 수 있다 - 막혀 있던 경고 발송·상태 전이를 실제로
    // 재개하려면 retryWarning()을 따로 호출해야 한다(이 메서드는 그 재시도를 하지 않는다).
    @Transactional
    public void unfreeze(UUID userId, UUID caseId, String password) {
        User actor = permissionGuard.require(userId, AdminPermission.CASE_SUPERVISE);
        try {
            reauthGuard.verify(actor, password);
        } catch (CustomException e) {
            adminActionAuditService.record(actor, AdminActionType.CASE_UNFREEZE, caseId, false, "재인증 실패");
            throw e;
        }

        findCase(caseId).unfreeze();
        adminActionAuditService.record(actor, AdminActionType.CASE_UNFREEZE, caseId, true, null);
    }

    // "경고 발송 재시도" - 사건 생성/증빙 승인 시점에 경고 메일 발송이 실패해 동결된 사건을 대상으로,
    // 막혀 있던 경고 발송과 그것이 게이트하던 상태 전이(WAITING 진입)를 다시 시도한다. 증빙이 전부
    // 승인됐는지를 그때와 동일한 기준으로 재계산해 어느 경고가 막혀 있었는지 판단한다 - 이미 WAITING
    // 이후 단계로 넘어간 사건은 재시도 대상이 아니다.
    @Transactional
    public void retryWarning(UUID userId, UUID caseId, String password) {
        User actor = permissionGuard.require(userId, AdminPermission.CASE_SUPERVISE);
        try {
            reauthGuard.verify(actor, password);
        } catch (CustomException e) {
            adminActionAuditService.record(actor, AdminActionType.CASE_WARNING_RETRY, caseId, false, "재인증 실패");
            throw e;
        }

        ReleaseCase releaseCase = findCase(caseId);
        if (releaseCase.getStatus() == ReleaseCaseStatus.WAITING
                || releaseCase.getStatus() == ReleaseCaseStatus.RELEASING
                || releaseCase.getStatus() == ReleaseCaseStatus.COMPLETED
                || releaseCase.getStatus() == ReleaseCaseStatus.CANCELED
                || releaseCase.getStatus() == ReleaseCaseStatus.DISPUTED) {
            adminActionAuditService.record(actor, AdminActionType.CASE_WARNING_RETRY, caseId, false, "재시도 대상 아님");
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        long approvedCount = evidenceRepository.countByReleaseCase_CaseIdAndReviewStatus(caseId, EvidenceReviewStatus.APPROVED);
        long requiredCount = confirmerRepository.findByPlan_PlanIdAndReportStatus(
                releaseCase.getPlan().getPlanId(), ReportStatus.MATCHED).size();

        boolean sent = (requiredCount > 0 && approvedCount >= requiredCount)
                ? releaseCaseWarningService.sendDisputeWarningsAndStartWaiting(releaseCase, releaseCase.getPlan().getWaitingDays())
                : releaseCaseWarningService.sendAuthorCancelWarningOrFreeze(releaseCase);

        if (sent) {
            releaseCase.unfreeze();
        }
        adminActionAuditService.record(actor, AdminActionType.CASE_WARNING_RETRY, caseId, sent, sent ? null : "발송 재시도 실패");
    }

    private ReleaseCase findCase(UUID caseId) {
        return releaseCaseRepository.findById(caseId)
                .orElseThrow(() -> new CustomException(ErrorCode.RELEASE_CASE_NOT_FOUND));
    }

    private EmailLog findLog(ReleaseCase releaseCase, UUID logId) {
        EmailLog log = emailLogRepository.findById(logId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!log.getPlan().getPlanId().equals(releaseCase.getPlan().getPlanId())) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return log;
    }
}
