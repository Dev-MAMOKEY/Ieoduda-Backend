package com.mamoki.ieojuda.domain.confirmer.service;

import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.confirmer.dto.DeathReportRequest;
import com.mamoki.ieojuda.domain.confirmer.dto.DeathReportResponse;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.entity.ReportStatus;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.plan.dto.PlanSnapshotDto;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanVersion;
import com.mamoki.ieojuda.domain.plan.repository.PlanVersionRepository;
import com.mamoki.ieojuda.domain.plan.service.PlanReadinessValidator;
import com.mamoki.ieojuda.domain.plan.service.PlanSnapshotService;
import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.releasecase.service.ReleaseCaseWarningService;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityToken;
import com.mamoki.ieojuda.domain.securitytoken.entity.SecurityTokenPurpose;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxService;
import com.mamoki.ieojuda.global.email.template.EmailBuilder;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.idempotency.service.IdempotencyGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

// 명세서 "사망 신고 이메일" 화면 - 지정 확인자가 사망 사실을 신고한다 (로그인 불필요, REPORT_DEATH 토큰이 곧 인증).
// issue #41 - 수락 시 발급된 ACCEPT_ROLE 토큰과는 별개인 REPORT_DEATH 전용 토큰만 여기서 받는다
// (역할 수락 토큰이 사망 신고의 영구 접근키로 재사용되지 않도록).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeathReportService {

    // issue #41 - 증빙 제출은 사건 생성 시점부터 일정 기간 내에 이뤄져야 하므로, 사망신고 토큰보다 짧게 둔다
    private static final long EVIDENCE_TOKEN_TTL_DAYS = 30;

    private final ConfirmerRepository confirmerRepository;
    private final ReleaseCaseRepository releaseCaseRepository;
    private final PlanVersionRepository planVersionRepository;
    private final PlanSnapshotService planSnapshotService;
    private final IdempotencyGuard idempotencyGuard;
    private final SecurityTokenService securityTokenService;
    private final EmailOutboxService emailOutboxService;
    private final AppProperties appProperties;
    private final PlanReadinessValidator planReadinessValidator;
    private final ReleaseCaseWarningService releaseCaseWarningService;

    @Transactional
    public DeathReportResponse report(String plainToken, DeathReportRequest request, String idempotencyKey) {
        SecurityToken token = securityTokenService.resolve(plainToken, SecurityTokenPurpose.REPORT_DEATH);
        Confirmer confirmer = token.getConfirmer();

        if (confirmer.getAcceptanceStatus() != AcceptanceStatus.ACCEPTED) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        if (confirmer.getReportStatus() != ReportStatus.NOT_REPORTED) {
            throw new CustomException(ErrorCode.ACCESS_LINK_ALREADY_USED);
        }
        idempotencyGuard.claim("death-report", idempotencyKey);

        confirmer.report(request == null ? null : request.deathDate());
        securityTokenService.consume(token);

        List<Confirmer> siblings = confirmerRepository.findByPlan_PlanIdAndConfirmIdNotAndReportStatus(
                confirmer.getPlan().getPlanId(), confirmer.getConfirmId(), ReportStatus.REPORTED);

        if (!siblings.isEmpty()) {
            Confirmer sibling = siblings.get(0);
            if (datesAgree(confirmer.getReportedDeathDate(), sibling.getReportedDeathDate())) {
                confirmer.markMatched();
                sibling.markMatched();
                ReleaseCase releaseCase = createReleaseCase(confirmer.getPlan());
                issueEvidenceUploadTokenAndSend(confirmer, releaseCase);
                issueEvidenceUploadTokenAndSend(sibling, releaseCase);
            } else {
                confirmer.markMismatched();
                sibling.markMismatched();
            }
        }

        return DeathReportResponse.from(confirmer);
    }

    // issue #41 - 증빙 제출은 노션 명세서상 "제출 안내 이메일"을 통해서만 진입하므로, 사건이 열리는 즉시
    // 매칭된 두 확인자 각각에게 자신만의 UPLOAD_EVIDENCE 토큰(사건 바인딩)을 발급해 이메일로 보낸다.
    private void issueEvidenceUploadTokenAndSend(Confirmer confirmer, ReleaseCase releaseCase) {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(EVIDENCE_TOKEN_TTL_DAYS);
        String plainToken = securityTokenService.issueForConfirmer(
                SecurityTokenPurpose.UPLOAD_EVIDENCE, confirmer, releaseCase, expiresAt);

        String secureLink = appProperties.getBaseUrl() + "/death-report?token=" + plainToken + "&caseId=" + releaseCase.getCaseId();
        EmailContent content = EmailBuilder.build(
                "공식 증빙 자료 제출",
                "사망 관련 공식 증빙 자료(사망진단서 등)를 제출해 주세요.",
                expiresAt.atZone(ZoneId.systemDefault()),
                secureLink,
                appProperties.getContactEmail()
        );
        emailOutboxService.enqueue(confirmer.getPlan(), null, EmailType.EVIDENCE_SUBMISSION_REQUEST, confirmer.getEmail(), content);
    }

    // 둘 다 사망일을 명시했고 그 값이 같을 때만 일치로 본다.
    // 하나라도 모르면(null)이거나 날짜가 다르면 불일치 처리 - 재확인/운영 검토로 전환한다.
    private boolean datesAgree(LocalDate a, LocalDate b) {
        return a != null && b != null && a.equals(b);
    }

    private ReleaseCase createReleaseCase(Plan plan) {
        if (releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(plan.getPlanId())
                .filter(existing -> existing.getCanceledAt() == null)
                .isPresent()) {
            throw new CustomException(ErrorCode.ACTIVE_RELEASE_CASE_EXISTS);
        }

        // 준비되지 않았거나 비활성인 계획에서 사건이 시작되지 않도록, 사건 생성 직전 계획 준비도를
        // 재검증한다 (봉인 이후에도 확인자 거절·항목 추가 등으로 상태가 바뀔 수 있어 독립적으로 확인).
        planReadinessValidator.validate(plan);

        int nextVersionNum = (int) planVersionRepository.countByPlan_PlanId(plan.getPlanId()) + 1;
        PlanSnapshotDto snapshot = planSnapshotService.buildSnapshot(plan);
        String snapshotJson = planSnapshotService.serialize(snapshot);
        String snapshotHash = planSnapshotService.hash(snapshotJson);

        PlanVersion planVersion = planVersionRepository.save(
                PlanVersion.builder().plan(plan).versionNum(nextVersionNum).snapshotData(snapshotJson).build());
        planVersion.seal(snapshotHash);

        // 위 존재 여부 검사만으로는 두 확인자의 신고가 정확히 동시에 매칭되는 경쟁 조건을 막을 수 없으므로,
        // DB의 부분 유니크 인덱스(활성 사건은 계획당 1개)가 최종 방어선이다 - 즉시 flush해서 위반 시 여기서 잡는다.
        ReleaseCase releaseCase;
        try {
            releaseCase = releaseCaseRepository.saveAndFlush(
                    ReleaseCase.builder().plan(plan).planVersion(planVersion).build());
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.ACTIVE_RELEASE_CASE_EXISTS);
        }
        releaseCase.confirmReport();
        releaseCase.awaitEvidence();
        // 사건이 대기 없이 바로 증빙 단계로 넘어가기 전에, 작성자에게 취소 링크를 반드시 먼저 보낸다.
        // 발송이 실패하면 사건은 이미 만들어졌으므로 되돌리지 않고 동결해 운영 검토로 넘긴다.
        releaseCaseWarningService.sendAuthorCancelWarningOrFreeze(releaseCase);
        return releaseCase;
    }
}
