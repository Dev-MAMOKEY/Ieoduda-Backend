package com.mamoki.ieojuda.domain.confirmer.service;

import com.mamoki.ieojuda.domain.confirmer.dto.DeathReportRequest;
import com.mamoki.ieojuda.domain.confirmer.dto.DeathReportResponse;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.entity.ReportStatus;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.plan.dto.PlanSnapshotDto;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanVersion;
import com.mamoki.ieojuda.domain.plan.repository.PlanVersionRepository;
import com.mamoki.ieojuda.domain.plan.service.PlanSnapshotService;
import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.idempotency.service.IdempotencyGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

// 명세서 "사망 신고 이메일" 화면 - 지정 확인자가 사망 사실을 신고한다 (로그인 불필요, 초대 토큰이 곧 인증).
// 수락 시 발급된 초대 토큰은 "수락 대기" 창구용 만료시각이 있지만, 이미 수락한 확인자에게는
// 이 토큰이 이후 언제든 다시 찾아와 신고할 수 있는 개인 접근키 역할도 겸한다 - 그래서 여기서는 만료 검사를 하지 않는다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeathReportService {

    private final ConfirmerRepository confirmerRepository;
    private final ReleaseCaseRepository releaseCaseRepository;
    private final PlanVersionRepository planVersionRepository;
    private final PlanSnapshotService planSnapshotService;
    private final IdempotencyGuard idempotencyGuard;

    @Transactional
    public DeathReportResponse report(String plainToken, DeathReportRequest request, String idempotencyKey) {
        Confirmer confirmer = findByToken(plainToken);

        if (confirmer.getAcceptanceStatus() != AcceptanceStatus.ACCEPTED) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        if (confirmer.getReportStatus() != ReportStatus.NOT_REPORTED) {
            throw new CustomException(ErrorCode.ACCESS_LINK_ALREADY_USED);
        }
        idempotencyGuard.claim("death-report", idempotencyKey);

        confirmer.report(request == null ? null : request.deathDate());

        List<Confirmer> siblings = confirmerRepository.findByPlan_PlanIdAndConfirmIdNotAndReportStatus(
                confirmer.getPlan().getPlanId(), confirmer.getConfirmId(), ReportStatus.REPORTED);

        if (!siblings.isEmpty()) {
            Confirmer sibling = siblings.get(0);
            if (datesAgree(confirmer.getReportedDeathDate(), sibling.getReportedDeathDate())) {
                confirmer.markMatched();
                sibling.markMatched();
                createReleaseCase(confirmer.getPlan());
            } else {
                confirmer.markMismatched();
                sibling.markMismatched();
            }
        }

        return DeathReportResponse.from(confirmer);
    }

    // 둘 다 모르는 경우, 둘 다 같은 날짜인 경우, 한쪽만 모르는 경우는 모두 일치로 본다 - 날짜가 서로 다를 때만 불일치
    private boolean datesAgree(LocalDate a, LocalDate b) {
        if (a == null || b == null) {
            return true;
        }
        return Objects.equals(a, b);
    }

    private void createReleaseCase(Plan plan) {
        if (releaseCaseRepository.findFirstByPlan_PlanIdOrderByCaseIdDesc(plan.getPlanId())
                .filter(existing -> existing.getCanceledAt() == null)
                .isPresent()) {
            throw new CustomException(ErrorCode.ACTIVE_RELEASE_CASE_EXISTS);
        }

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
    }

    private Confirmer findByToken(String plainToken) {
        return confirmerRepository.findByInviteToken(TokenProvider.hashToken(plainToken))
                .orElseThrow(() -> new CustomException(ErrorCode.TOKEN_INVALID));
    }
}
