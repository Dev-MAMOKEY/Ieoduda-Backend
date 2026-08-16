package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.confirmer.repository.DisputeContactRepository;
import com.mamoki.ieojuda.domain.plan.dto.PlanResponse;
import com.mamoki.ieojuda.domain.plan.dto.ReleasePolicyRequest;
import com.mamoki.ieojuda.domain.plan.dto.ReleasePolicyResponse;
import com.mamoki.ieojuda.domain.plan.dto.ReleaseSettingsResponse;
import com.mamoki.ieojuda.domain.plan.dto.SelfWarningEmailRequest;
import com.mamoki.ieojuda.domain.plan.dto.SelfWarningEmailResponse;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.contract.EmailContent;
import com.mamoki.ieojuda.global.email.contract.EmailSendResult;
import com.mamoki.ieojuda.global.email.sender.EmailSender;
import com.mamoki.ieojuda.global.email.template.EmailBuilder;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.email.token.TokenValidator;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

// 계획(Plan)은 더 이상 사용자가 "만드는" 대상이 아니다 - 회원가입 시 자동으로 1개 생성되고(AuthService),
// 사망확인/증빙검토/발송 파이프라인(death_confirmers, release_cases 등)이 매달리는 앵커 역할만 한다.
// 그래서 이 서비스에는 생성/수정 로직이 없고 조회·비활성화·대기설정만 남는다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanService {

    private static final int MIN_WAITING_DAYS = 7;
    private static final int MAX_WAITING_DAYS = 30;

    private final PlanRepository planRepository;
    private final DisputeContactRepository disputeContactRepository;
    private final EmailSender emailSender;
    private final AppProperties appProperties;

    public PlanResponse getPlan(Long planId) {
        return PlanResponse.from(findPlan(planId));
    }

    // 로그인한 사용자가 자기 planId를 모를 때(로그인 직후 등) 조회
    public PlanResponse getMyPlan(Long userId) {
        Plan plan = planRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAN_NOT_FOUND));
        return PlanResponse.from(plan);
    }

    @Transactional
    public PlanResponse deactivate(Long planId) {
        Plan plan = findPlan(planId);
        plan.deactivate();
        return PlanResponse.from(plan);
    }

    // "대기 이의제기 수정" 화면 - 저장된 대기기간/본인경고이메일/이의제기연락처를 한 번에 조회
    public ReleaseSettingsResponse getReleaseSettings(Long planId) {
        Plan plan = findPlan(planId);
        var disputeContact = disputeContactRepository.findFirstByPlan_PlanIdOrderByContactIdDesc(planId).orElse(null);
        return ReleaseSettingsResponse.of(plan, disputeContact);
    }

    // "대기 이의제기 설정" 화면 - 대기 기간 저장
    @Transactional
    public ReleasePolicyResponse updateReleasePolicy(Long planId, ReleasePolicyRequest request) {
        if (request.waitingDays() == null
                || request.waitingDays() < MIN_WAITING_DAYS
                || request.waitingDays() > MAX_WAITING_DAYS) {
            throw new CustomException(ErrorCode.INVALID_WAITING_PERIOD);
        }
        Plan plan = findPlan(planId);
        plan.updateWaitingDays(request.waitingDays());
        return ReleasePolicyResponse.from(plan);
    }

    // "대기 이의제기 설정" 화면 - 본인 경고 이메일 등록 + 검증 메일 발송
    @Transactional
    public SelfWarningEmailResponse requestSelfWarningEmailVerification(Long planId, SelfWarningEmailRequest request) {
        Plan plan = findPlan(planId);

        String plainToken = TokenProvider.generatePlainToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(appProperties.getInviteTokenTtlHours());
        plan.requestSelfWarningEmailVerification(request.email(), TokenProvider.hashToken(plainToken), expiresAt);

        String secureLink = appProperties.getBaseUrl() + "/self-warning-email/verify/" + plainToken;
        EmailContent content = EmailBuilder.build(
                "본인 경고 메일",
                "링크를 눌러 이 이메일 주소를 본인 경고 메일 수신처로 확인해 주세요.",
                expiresAt.atZone(ZoneId.systemDefault()),
                secureLink,
                appProperties.getContactEmail()
        );
        EmailSendResult sendResult = emailSender.send(request.email(), content);

        return SelfWarningEmailResponse.of(plan, sendResult.success());
    }

    // 본인 경고 이메일 검증 링크 클릭 - 로그인 불필요(토큰 자체가 증명), planId도 토큰으로 역추적
    @Transactional
    public void verifySelfWarningEmail(String plainToken) {
        Plan plan = planRepository.findBySelfWarningVerifyToken(TokenProvider.hashToken(plainToken))
                .orElseThrow(() -> new CustomException(ErrorCode.TOKEN_INVALID));

        if (Boolean.TRUE.equals(plan.getSelfWarningEmailVerified())) {
            return; // 이미 검증됨 - 링크 재클릭은 그냥 성공 처리
        }

        Instant expiresAt = plan.getSelfWarningVerifyTokenExpiresAt().atZone(ZoneId.systemDefault()).toInstant();
        if (TokenValidator.isExpired(expiresAt, Instant.now())) {
            throw new CustomException(ErrorCode.ACCESS_LINK_EXPIRED);
        }

        plan.verifySelfWarningEmail();
    }

    private Plan findPlan(Long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAN_NOT_FOUND));
    }
}
