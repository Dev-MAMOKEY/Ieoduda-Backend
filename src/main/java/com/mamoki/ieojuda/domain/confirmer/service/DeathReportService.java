package com.mamoki.ieojuda.domain.confirmer.service;

import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.confirmer.dto.DeathReportInviteResponse;
import com.mamoki.ieojuda.domain.confirmer.dto.DeathReportRequest;
import com.mamoki.ieojuda.domain.confirmer.dto.DeathReportResponse;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.entity.ReportStatus;
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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

// 명세서 "사망 신고 이메일" 화면 - 지정 확인자가 사망 사실을 신고한다 (로그인 불필요, REPORT_DEATH 토큰이 곧 인증).
// issue #41 - 수락 시 발급된 ACCEPT_ROLE 토큰과는 별개인 REPORT_DEATH 전용 토큰만 여기서 받는다
// (역할 수락 토큰이 사망 신고의 영구 접근키로 재사용되지 않도록).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeathReportService {

    // issue #41 - 증빙 제출은 사건 생성 시점부터 일정 기간 내에 이뤄져야 하므로, 사망신고 토큰보다 짧게 둔다
    private static final long EVIDENCE_TOKEN_TTL_DAYS = 30;

    private final ReleaseCaseRepository releaseCaseRepository;
    private final PlanVersionRepository planVersionRepository;
    private final PlanSnapshotService planSnapshotService;
    private final IdempotencyGuard idempotencyGuard;
    private final SecurityTokenService securityTokenService;
    private final EmailOutboxService emailOutboxService;
    private final AppProperties appProperties;
    private final PlanReadinessValidator planReadinessValidator;
    private final ReleaseCaseWarningService releaseCaseWarningService;

    // 신고 조회 - 사망 신고 이메일의 링크로 진입했을 때 화면에 보여줄 정보. 만료·사용·폐기·목적 불일치 링크는 차단
    @Transactional
    public DeathReportInviteResponse getInvite(String plainToken) {
        SecurityToken token = securityTokenService.resolve(plainToken, SecurityTokenPurpose.REPORT_DEATH);
        Confirmer confirmer = token.getConfirmer();

        String ownerName = confirmer.getPlan().getUser().getName();
        return DeathReportInviteResponse.of(confirmer, ownerName, token.getExpiresAt(), appProperties.getContactEmail());
    }

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

        // issue #41 재설계 - 매칭 판정(날짜 비교)은 더 이상 여기서 하지 않는다. 상대 확인자 상태와 무관하게
        // 사건을 찾거나 만들고, 이 확인자 몫의 증빙 제출 토큰을 즉시 발급해 응답에 담아 돌려준다 - 매칭 판정은
        // 두 확인자 모두 증빙까지 제출을 마친 시점(EvidenceSubmitService)으로 미뤄진다.
        ReleaseCase releaseCase = resolveActiveReleaseCase(confirmer.getPlan());
        String evidenceUploadToken = issueEvidenceUploadTokenAndSend(confirmer, releaseCase);

        return DeathReportResponse.of(confirmer, releaseCase.getCaseId(), evidenceUploadToken);
    }

    // issue #41 재설계 - 이 계획에 이미 진행 중인 사건이 있으면(다른 확인자가 먼저 신고했다면) 그대로 재사용하고,
    // 없으면(첫 신고) 새로 만든다. "사건 존재 = 매칭 완료"라는 이전 전제가 더 이상 성립하지 않는다.
    private ReleaseCase resolveActiveReleaseCase(Plan plan) {
        return findActiveReleaseCase(plan).orElseGet(() -> createReleaseCase(plan));
    }

    private Optional<ReleaseCase> findActiveReleaseCase(Plan plan) {
        return releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(plan.getPlanId())
                .filter(existing -> existing.getCanceledAt() == null);
    }

    // issue #41 - 증빙 제출은 노션 명세서상 "제출 안내 이메일"을 통해서만 진입하므로, 신고를 받는 즉시 이
    // 확인자 몫의 UPLOAD_EVIDENCE 토큰(사건 바인딩)을 발급해 이메일로도 보낸다(세션이 끊겨도 돌아올 수 있도록
    // 하는 백업 채널) - 동시에 이 토큰 문자열을 반환해, 프런트가 응답만으로 바로 증빙 제출 화면으로 넘어갈 수 있게 한다.
    private String issueEvidenceUploadTokenAndSend(Confirmer confirmer, ReleaseCase releaseCase) {
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
        return plainToken;
    }

    private ReleaseCase createReleaseCase(Plan plan) {
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

        // 두 확인자가 거의 동시에 첫 신고를 하면 둘 다 "활성 사건 없음"으로 보고 동시에 생성을 시도할 수 있다.
        // DB의 부분 유니크 인덱스(활성 사건은 계획당 1개)가 최종 방어선이므로 즉시 flush해서 위반 시 여기서 잡되,
        // 이건 이제 정상적인 경쟁 상황(두 번째 확인자의 신고)이므로 에러로 취급하지 않고 먼저 만들어진 사건을 찾아 재사용한다.
        ReleaseCase releaseCase;
        try {
            releaseCase = releaseCaseRepository.saveAndFlush(
                    ReleaseCase.builder().plan(plan).planVersion(planVersion).build());
        } catch (DataIntegrityViolationException e) {
            return findActiveReleaseCase(plan)
                    .orElseThrow(() -> new CustomException(ErrorCode.ACTIVE_RELEASE_CASE_EXISTS));
        }
        releaseCase.confirmReport();
        releaseCase.awaitEvidence();
        // 사건이 대기 없이 바로 증빙 단계로 넘어가기 전에, 작성자에게 취소 링크를 반드시 먼저 보낸다.
        // 발송이 실패하면 사건은 이미 만들어졌으므로 되돌리지 않고 동결해 운영 검토로 넘긴다.
        releaseCaseWarningService.sendAuthorCancelWarningOrFreeze(releaseCase);
        return releaseCase;
    }
}
