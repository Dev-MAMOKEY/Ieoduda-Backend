package com.mamoki.ieojuda.domain.audit.service;

import com.mamoki.ieojuda.domain.audit.dto.EmailDeliveryResponse;
import com.mamoki.ieojuda.domain.audit.entity.EmailLog;
import com.mamoki.ieojuda.domain.audit.repository.EmailLogRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.contract.EmailSendResult;
import com.mamoki.ieojuda.global.email.sender.EmailSender;
import com.mamoki.ieojuda.global.email.template.EmailBuilder;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

// 명세서 "이메일 발송 감사" 화면 - 서비스 운영자가 발송·반송·열람 이력을 조회하고 재시도/동결만 수행 (본문·패키지 내용은 접근 불가)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailAuditService {

    private final ReleaseCaseRepository releaseCaseRepository;
    private final EmailLogRepository emailLogRepository;
    private final EmailSender emailSender;
    private final AppProperties appProperties;

    public List<EmailDeliveryResponse> getDeliveries(Long caseId) {
        ReleaseCase releaseCase = findCase(caseId);
        return emailLogRepository.findByPlan_PlanIdOrderBySentAtDesc(releaseCase.getPlan().getPlanId()).stream()
                .map(EmailDeliveryResponse::from)
                .toList();
    }

    // "재시도 정책 실행하기" - 이 발송 건과 연결된 담당자에게 새 초대 링크를 발급해 재발송
    @Transactional
    public EmailDeliveryResponse retry(Long caseId, Long logId) {
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
        String plainToken = TokenProvider.generatePlainToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(appProperties.getInviteTokenTtlHours());
        recipient.issueInviteToken(TokenProvider.hashToken(plainToken), expiresAt);

        String secureLink = appProperties.getBaseUrl() + "/recipient-acceptances/" + plainToken;
        EmailContent content = EmailBuilder.build(
                "사후 인계 안내",
                "역할 수락 여부를 다시 확인해 주세요.",
                expiresAt.atZone(ZoneId.systemDefault()),
                secureLink,
                appProperties.getContactEmail()
        );
        EmailSendResult result = emailSender.send(recipient.getEmail(), content);

        if (result.success()) {
            log.markRetried(result.messageId());
        } else {
            log.markBounced();
        }
        return EmailDeliveryResponse.from(log);
    }

    // "사건 동결하기" - 이후 발송·단계 전환을 전부 막음
    @Transactional
    public void freeze(Long caseId) {
        findCase(caseId).freeze();
    }

    private ReleaseCase findCase(Long caseId) {
        return releaseCaseRepository.findById(caseId)
                .orElseThrow(() -> new CustomException(ErrorCode.RELEASE_CASE_NOT_FOUND));
    }

    private EmailLog findLog(ReleaseCase releaseCase, Long logId) {
        EmailLog log = emailLogRepository.findById(logId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!log.getPlan().getPlanId().equals(releaseCase.getPlan().getPlanId())) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return log;
    }
}
