package com.mamoki.ieojuda.domain.stage.service;

import com.mamoki.ieojuda.domain.audit.entity.EmailLog;
import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.audit.repository.EmailLogRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.stage.dto.HandoverStageResponse;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import com.mamoki.ieojuda.domain.stage.repository.HandoverStageRepository;
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

// 명세서 "단계 완료 / 대체 담당자" 화면 - 역할 담당자·서비스 운영자 공용
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HandoverStageService {

    private final ReleaseCaseRepository releaseCaseRepository;
    private final HandoverStageRepository handoverStageRepository;
    private final RecipientRepository recipientRepository;
    private final EmailLogRepository emailLogRepository;
    private final EmailSender emailSender;
    private final AppProperties appProperties;

    public HandoverStageResponse getStage(Long caseId, Long stageId) {
        ReleaseCase releaseCase = releaseCaseRepository.findById(caseId)
                .orElseThrow(() -> new CustomException(ErrorCode.RELEASE_CASE_NOT_FOUND));
        return HandoverStageResponse.from(findStage(releaseCase, stageId));
    }

    // 무응답·영구반송·문제신고로 대체 담당자에게 전환. 대체 담당자가 없으면 사건을 차단 상태로 유지한다.
    @Transactional
    public HandoverStageResponse fallback(Long caseId, Long stageId) {
        ReleaseCase releaseCase = releaseCaseRepository.findById(caseId)
                .orElseThrow(() -> new CustomException(ErrorCode.RELEASE_CASE_NOT_FOUND));
        if (Boolean.TRUE.equals(releaseCase.getFrozen())) {
            throw new CustomException(ErrorCode.RELEASE_CASE_FROZEN);
        }

        HandoverStage stage = findStage(releaseCase, stageId);
        Recipient current = stage.getRecipient();

        Recipient backup = recipientRepository.findByBackupFor_AssigneeId(current.getAssigneeId())
                .orElse(null);
        if (backup == null) {
            stage.block();
            throw new CustomException(ErrorCode.FALLBACK_RECIPIENT_MISSING);
        }

        stage.fallbackTo(backup);
        sendHandoffInvite(stage, backup);

        return HandoverStageResponse.from(stage);
    }

    private void sendHandoffInvite(HandoverStage stage, Recipient recipient) {
        String plainToken = TokenProvider.generatePlainToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(appProperties.getInviteTokenTtlHours());
        recipient.issueInviteToken(TokenProvider.hashToken(plainToken), expiresAt);

        String secureLink = appProperties.getBaseUrl() + "/recipient-acceptances/" + plainToken;
        EmailContent content = EmailBuilder.build(
                "사후 인계 안내 (대체 담당자)",
                "이전 담당자가 응답하지 않아 대체 담당자로 지정되었습니다. 역할 수락 여부를 확인해 주세요.",
                expiresAt.atZone(ZoneId.systemDefault()),
                secureLink,
                appProperties.getContactEmail()
        );
        EmailSendResult result = emailSender.send(recipient.getEmail(), content);

        emailLogRepository.save(EmailLog.builder()
                .plan(stage.getPlan())
                .handoverStage(stage)
                .emailType(EmailType.POSTHUMOUS_HANDOFF_LINK)
                .recipientEmail(recipient.getEmail())
                .messageId(result.messageId())
                .build());
        stage.send();
    }

    private HandoverStage findStage(ReleaseCase releaseCase, Long stageId) {
        HandoverStage stage = handoverStageRepository.findById(stageId)
                .orElseThrow(() -> new CustomException(ErrorCode.HANDOVER_STAGE_NOT_FOUND));
        if (stage.getReleaseCase() == null || !stage.getReleaseCase().getCaseId().equals(releaseCase.getCaseId())) {
            throw new CustomException(ErrorCode.HANDOVER_STAGE_NOT_FOUND);
        }
        return stage;
    }
}
