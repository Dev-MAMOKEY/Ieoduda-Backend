package com.mamoki.ieojuda.domain.stage.service;

import com.mamoki.ieojuda.domain.account.entity.AdminPermission;
import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.audit.entity.AdminActionType;
import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.audit.repository.EmailLogRepository;
import com.mamoki.ieojuda.domain.audit.service.AdminActionAuditService;
import com.mamoki.ieojuda.domain.postaccess.entity.AccessToken;
import com.mamoki.ieojuda.domain.postaccess.repository.AccessTokenRepository;
import com.mamoki.ieojuda.domain.postaccess.repository.PackageActionCompletionRepository;
import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.domain.stage.dto.HandoverStageResponse;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStageStatus;
import com.mamoki.ieojuda.domain.stage.repository.HandoverStageRepository;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxService;
import com.mamoki.ieojuda.global.email.template.EmailBuilder;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.security.PermissionGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.UUID;
import java.util.List;

// 명세서 "단계 완료 / 대체 담당자" 화면 - 역할 담당자·서비스 운영자 공용
// issue #59 - 운영자 경로(getStage/fallback)는 CASE_SUPERVISE 세부 권한 검사
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HandoverStageService {

    private final ReleaseCaseRepository releaseCaseRepository;
    private final HandoverStageRepository handoverStageRepository;
    private final RecipientRepository recipientRepository;
    private final EmailOutboxService emailOutboxService;
    private final AppProperties appProperties;
    private final PermissionGuard permissionGuard;
    private final PackageActionCompletionRepository packageActionCompletionRepository;
    private final AccessTokenRepository accessTokenRepository;
    private final SecurityTokenService securityTokenService;
    private final AdminActionAuditService adminActionAuditService;

    public HandoverStageResponse getStage(UUID userId, UUID caseId, UUID stageId) {
        permissionGuard.require(userId, AdminPermission.CASE_SUPERVISE);
        ReleaseCase releaseCase = releaseCaseRepository.findById(caseId)
                .orElseThrow(() -> new CustomException(ErrorCode.RELEASE_CASE_NOT_FOUND));
        return HandoverStageResponse.from(findStage(releaseCase, stageId));
    }

    // 무응답·영구반송·문제신고로 대체 담당자에게 전환. 대체 담당자가 없으면 사건을 차단 상태로 유지한다.
    // issue #47 - "대체 담당자가 있다"는 backupFor 관계가 존재한다는 뜻일 뿐, 그 사람이 생전에 역할을
    // 수락했다는 보장은 아니다. 거절·대기·만료 상태인 대체 담당자에게 민감한 사후 링크를 보내면 안 되므로,
    // 관계 존재 여부가 아니라 "지금 전환해도 되는 대체 담당자인지"를 별도로 판정한다.
    @Transactional
    public HandoverStageResponse fallback(UUID userId, UUID caseId, UUID stageId) {
        User actor = permissionGuard.require(userId, AdminPermission.CASE_SUPERVISE);
        ReleaseCase releaseCase = releaseCaseRepository.findById(caseId)
                .orElseThrow(() -> new CustomException(ErrorCode.RELEASE_CASE_NOT_FOUND));
        if (Boolean.TRUE.equals(releaseCase.getFrozen())) {
            throw new CustomException(ErrorCode.RELEASE_CASE_FROZEN);
        }

        HandoverStage stage = findStage(releaseCase, stageId);
        Recipient current = stage.getRecipient();
        HandoverStageStatus stageStatusBeforeFallback = stage.getStatus();

        Recipient backup = recipientRepository.findByBackupFor_AssigneeId(current.getAssigneeId())
                .orElse(null);
        ErrorCode rejectionReason = resolveFallbackRejection(backup, current);
        if (rejectionReason != null) {
            stage.block();
            adminActionAuditService.record(actor, AdminActionType.STAGE_FALLBACK, stageId, false,
                    describeFallbackFailure(current, backup, stageStatusBeforeFallback, rejectionReason));
            throw new CustomException(rejectionReason);
        }

        stage.fallbackTo(backup);
        sendHandoffInvite(stage, backup, InviteKind.FALLBACK);
        adminActionAuditService.record(actor, AdminActionType.STAGE_FALLBACK, stageId, true,
                describeFallbackSuccess(current, backup, stageStatusBeforeFallback));

        return HandoverStageResponse.from(stage);
    }

    // issue #47 완료 조건 - "ACCEPTED 상태의 대체 담당자만 전환 대상이 된다".
    // 대체 담당자가 없거나 수락하지 않았으면 FALLBACK_RECIPIENT_MISSING/RECIPIENT_NOT_ACCEPTED를 반환하고,
    // 문제가 없으면 null을 반환한다(정상 전환 가능).
    // 공개 범주(disclosureScope) 일치는 대체 담당자 등록 시점(RecipientService.registerBackup)에 이미
    // 주 담당자와 같은 값으로만 생성되도록 구조적으로 보장되지만, 다른 경로로 어긋난 데이터가 들어오는 상황까지
    // 대비해 전환 직전에 한 번 더 확인한다(issue #45와 같은 이중 방어 원칙) - 이 경우도 "쓸 수 있는 대체
    // 담당자가 없다"와 동일하게 취급해 FALLBACK_RECIPIENT_MISSING으로 응답한다.
    private ErrorCode resolveFallbackRejection(Recipient backup, Recipient current) {
        if (backup == null) {
            return ErrorCode.FALLBACK_RECIPIENT_MISSING;
        }
        if (backup.getAcceptanceStatus() != AcceptanceStatus.ACCEPTED) {
            return ErrorCode.RECIPIENT_NOT_ACCEPTED;
        }
        if (backup.getDisclosureScope() != current.getDisclosureScope()) {
            return ErrorCode.FALLBACK_RECIPIENT_MISSING;
        }
        return null;
    }

    private String describeFallbackSuccess(Recipient current, Recipient backup, HandoverStageStatus stageStatusBeforeFallback) {
        return "%s 상태로 인해 %s에서 %s로 전환됨".formatted(
                stageStatusBeforeFallback, current.getAssigneeId(), backup.getAssigneeId());
    }

    private String describeFallbackFailure(Recipient current, Recipient backup, HandoverStageStatus stageStatusBeforeFallback,
                                            ErrorCode rejectionReason) {
        if (rejectionReason == ErrorCode.FALLBACK_RECIPIENT_MISSING && backup == null) {
            return "%s 상태에서 %s의 대체 담당자가 등록되어 있지 않아 차단됨".formatted(stageStatusBeforeFallback, current.getAssigneeId());
        }
        if (rejectionReason == ErrorCode.RECIPIENT_NOT_ACCEPTED) {
            return "%s 상태에서 대체 담당자 %s가 역할을 수락하지 않아(%s) 차단됨".formatted(
                    stageStatusBeforeFallback, backup.getAssigneeId(), backup.getAcceptanceStatus());
        }
        return "%s 상태에서 대체 담당자 %s의 공개 범주가 주 담당자와 달라 차단됨".formatted(
                stageStatusBeforeFallback, backup.getAssigneeId());
    }

    // 스케줄러 - 대기 기간 만료 시, 확정된 실행 순서대로 담당자 한 명당 단계 하나씩 만들고 1단계 담당자에게만 발송한다
    // (다음 담당자는 1단계가 완료·반송·문제신고로 넘어갈 때 뒤이어 발송된다 - 이 메서드는 최초 진입만 담당)
    @Transactional
    public void createStagesAndDispatchFirst(ReleaseCase releaseCase, List<Recipient> orderedRecipients) {
        List<HandoverStage> stages = new ArrayList<>();
        int stageOrder = 0;
        for (Recipient recipient : orderedRecipients) {
            HandoverStage stage = handoverStageRepository.save(HandoverStage.builder()
                    .plan(releaseCase.getPlan())
                    .recipient(recipient)
                    .stageOrder(stageOrder++)
                    .build());
            stage.assignToCase(releaseCase);
            stages.add(stage);
        }

        if (!stages.isEmpty()) {
            HandoverStage firstStage = stages.get(0);
            sendHandoffInvite(firstStage, firstStage.getRecipient(), InviteKind.INITIAL);
        }
    }

    // issue #78 - 역할별 사후 패키지(#77)에서 행동 하나가 완료될 때마다 호출된다. 같은 단계의 여러
    // 행동이 동시에 완료 요청되어도 "전부 완료 → 단계 완료 → 다음 단계 발송"이 한 번만 일어나도록,
    // 이 단계 행에 비관적 잠금을 걸고 그 안에서 완료 개수를 다시 센다(호출자가 넘긴 개수는 신뢰하지 않음).
    @Transactional
    public void completeStageIfAllActionsDone(UUID stageId, int totalActionCount) {
        HandoverStage stage = handoverStageRepository.findByIdForUpdate(stageId)
                .orElseThrow(() -> new CustomException(ErrorCode.HANDOVER_STAGE_NOT_FOUND));
        // SENT 상태에서만 완료로 전이한다 - 이미 완료됐다면(동시 요청) 중복 발송 방지, BLOCKED/BOUNCED/
        // FALLBACK 등 다른 상태라면 "BLOCKED 단계는 다음 단계를 열지 않는다" 규칙을 여기서도 지킨다.
        if (stage.getStatus() != HandoverStageStatus.SENT) {
            return;
        }

        long completedCount = packageActionCompletionRepository.countByHandoverStage_StageId(stageId);
        if (completedCount < totalActionCount) {
            return;
        }

        stage.complete();
        dispatchNextOrCompleteCase(stage);
    }

    // 다음 stageOrder 담당자에게 발송하거나, 마지막 단계였다면 사건 전체를 완료 처리한다.
    // BLOCKED 단계는 애초에 행동을 완료할 수 없어 이 경로를 타지 않으므로, 다음 단계가 자동으로 열리지 않는다.
    private void dispatchNextOrCompleteCase(HandoverStage stage) {
        HandoverStage next = handoverStageRepository
                .findFirstByReleaseCase_CaseIdAndStageOrderGreaterThanOrderByStageOrderAsc(
                        stage.getReleaseCase().getCaseId(), stage.getStageOrder())
                .orElse(null);
        if (next == null) {
            ReleaseCase releaseCase = stage.getReleaseCase();
            releaseCase.complete();
            // issue #41 - 사건 종료(완료) 시 이의 제기 창구로 쓰이던 토큰을 폐기한다
            securityTokenService.revokeAllForCase(releaseCase, SecurityTokenPurpose.RAISE_OBJECTION);
            return;
        }
        // 정상적으로 자기 순서가 돌아온 것이지 대체 담당자로 전환된 게 아니므로 INITIAL
        sendHandoffInvite(next, next.getRecipient(), InviteKind.INITIAL);
    }

    // issue #79 - 최초 발송(INITIAL)과 대체 담당자 전환(FALLBACK)은 문구가 달라야 한다.
    // 링크도 생전 역할 수락 화면이 아니라 사후 인증 화면(/posthumous-access/{token})을 가리킨다.
    //
    // 버그 수정: 링크만 /posthumous-access/로 바꾸고 토큰은 여전히 recipient.issueInviteToken()
    // (생전 역할수락용 role_assignees.invite_token)에 저장하고 있었다. issue #76의 검증 API는
    // AccessToken 테이블(posthumouse_access_tokens)만 조회하므로, 그 상태로는 발송되는 링크가
    // 전부 "토큰 없음"으로 실패했다 - AccessToken을 이 단계(stage)에 실제로 발급하도록 고쳤다.
    private void sendHandoffInvite(HandoverStage stage, Recipient recipient, InviteKind kind) {
        String plainToken = TokenProvider.generatePlainToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(appProperties.getInviteTokenTtlHours());
        accessTokenRepository.save(AccessToken.builder()
                .handoverStage(stage)
                .tokenHash(TokenProvider.hashToken(plainToken))
                .expiresAt(expiresAt)
                .build());

        String secureLink = appProperties.getBaseUrl() + "/posthumous-access/" + plainToken;
        EmailContent content = kind == InviteKind.INITIAL
                ? EmailBuilder.build(
                        "사후 인계 안내",
                        recipient.getRoleType().label() + " 역할로 전달드릴 내용이 있습니다. 본인 확인 후 확인해 주세요.",
                        expiresAt.atZone(ZoneId.systemDefault()),
                        secureLink,
                        appProperties.getContactEmail())
                : EmailBuilder.build(
                        "사후 인계 안내 (대체 담당자)",
                        "이전 담당자가 응답하지 않아 대체 담당자로 지정되었습니다. 역할 수락 여부를 확인해 주세요.",
                        expiresAt.atZone(ZoneId.systemDefault()),
                        secureLink,
                        appProperties.getContactEmail());
        // 실제 SMTP 발송은 EmailOutboxScheduler가 담당한다. stage.send()/EmailLog 기록도
        // 발송이 실제로 성공했을 때만 워커가 수행하므로, 여기서는 무조건 SENT로 기록되는 일이 없다.
        emailOutboxService.enqueue(stage.getPlan(), stage, EmailType.POSTHUMOUS_HANDOFF_LINK, recipient.getEmail(), content);
    }

    private HandoverStage findStage(ReleaseCase releaseCase, UUID stageId) {
        HandoverStage stage = handoverStageRepository.findById(stageId)
                .orElseThrow(() -> new CustomException(ErrorCode.HANDOVER_STAGE_NOT_FOUND));
        if (stage.getReleaseCase() == null || !stage.getReleaseCase().getCaseId().equals(releaseCase.getCaseId())) {
            throw new CustomException(ErrorCode.HANDOVER_STAGE_NOT_FOUND);
        }
        return stage;
    }
}
