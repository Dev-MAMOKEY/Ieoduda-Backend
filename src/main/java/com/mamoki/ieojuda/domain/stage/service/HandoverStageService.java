package com.mamoki.ieojuda.domain.stage.service;

import com.mamoki.ieojuda.domain.account.entity.AdminPermission;
import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.audit.repository.EmailLogRepository;
import com.mamoki.ieojuda.domain.postaccess.entity.AccessToken;
import com.mamoki.ieojuda.domain.postaccess.repository.AccessTokenRepository;
import com.mamoki.ieojuda.domain.postaccess.repository.PackageActionCompletionRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
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

    public HandoverStageResponse getStage(Long userId, Long caseId, Long stageId) {
        permissionGuard.require(userId, AdminPermission.CASE_SUPERVISE);
        ReleaseCase releaseCase = releaseCaseRepository.findById(caseId)
                .orElseThrow(() -> new CustomException(ErrorCode.RELEASE_CASE_NOT_FOUND));
        return HandoverStageResponse.from(findStage(releaseCase, stageId));
    }

    // 무응답·영구반송·문제신고로 대체 담당자에게 전환. 대체 담당자가 없으면 사건을 차단 상태로 유지한다.
    @Transactional
    public HandoverStageResponse fallback(Long userId, Long caseId, Long stageId) {
        permissionGuard.require(userId, AdminPermission.CASE_SUPERVISE);
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
        sendHandoffInvite(stage, backup, InviteKind.FALLBACK);

        return HandoverStageResponse.from(stage);
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
    public void completeStageIfAllActionsDone(Long stageId, int totalActionCount) {
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
            stage.getReleaseCase().complete();
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

    private HandoverStage findStage(ReleaseCase releaseCase, Long stageId) {
        HandoverStage stage = handoverStageRepository.findById(stageId)
                .orElseThrow(() -> new CustomException(ErrorCode.HANDOVER_STAGE_NOT_FOUND));
        if (stage.getReleaseCase() == null || !stage.getReleaseCase().getCaseId().equals(releaseCase.getCaseId())) {
            throw new CustomException(ErrorCode.HANDOVER_STAGE_NOT_FOUND);
        }
        return stage;
    }
}
