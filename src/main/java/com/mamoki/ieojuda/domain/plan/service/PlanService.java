package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.audit.entity.EmailType;
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
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxService;
import com.mamoki.ieojuda.global.email.template.EmailBuilder;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.email.token.TokenValidator;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
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
    private final PlanOwnershipReader planOwnershipReader;
    private final DisputeContactRepository disputeContactRepository;
    private final EmailOutboxService emailOutboxService;
    private final AppProperties appProperties;

    public PlanResponse getPlan(UUID userId, UUID planId) {
        return PlanResponse.from(planOwnershipReader.findOwnedPlan(userId, planId));
    }

    @Transactional
    public PlanResponse deactivate(UUID userId, UUID planId) {
        Plan plan = planOwnershipReader.findOwnedPlan(userId, planId);
        plan.deactivate();
        return PlanResponse.from(plan);
    }

    // "대기 이의제기 수정" 화면 - 저장된 대기기간/본인경고이메일/이의제기연락처를 한 번에 조회
    public ReleaseSettingsResponse getReleaseSettings(UUID userId, UUID planId) {
        Plan plan = planOwnershipReader.findOwnedPlan(userId, planId);
        var disputeContact = disputeContactRepository.findFirstByPlan_PlanIdOrderByContactIdDesc(planId).orElse(null);
        return ReleaseSettingsResponse.of(plan, disputeContact);
    }

    // "대기 이의제기 설정" 화면 - 대기 기간 저장
    // 소유권 검증을 범위 검증보다 먼저 수행한다 - 순서가 반대면 "타인 planId + 잘못된 값" 조합이 400을 돌려줘 존재를 흘린다
    @Transactional
    public ReleasePolicyResponse updateReleasePolicy(UUID userId, UUID planId, ReleasePolicyRequest request) {
        Plan plan = planOwnershipReader.findOwnedPlan(userId, planId);
        if (request.waitingDays() == null
                || request.waitingDays() < MIN_WAITING_DAYS
                || request.waitingDays() > MAX_WAITING_DAYS) {
            throw new CustomException(ErrorCode.INVALID_WAITING_PERIOD);
        }
        plan.updateWaitingDays(request.waitingDays());
        return ReleasePolicyResponse.from(plan);
    }

    // "대기 이의제기 설정" 화면 - 본인 경고 이메일 등록 + 검증 메일 발송
    @Transactional
    public SelfWarningEmailResponse requestSelfWarningEmailVerification(UUID userId, UUID planId, SelfWarningEmailRequest request) {
        Plan plan = planOwnershipReader.findOwnedPlan(userId, planId);

        String plainToken = TokenProvider.generatePlainToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(appProperties.getInviteTokenTtlHours());
        plan.requestSelfWarningEmailVerification(request.email(), TokenProvider.hashToken(plainToken), expiresAt);

        String secureLink = appProperties.getBaseUrl() + "/self-warning-email?token=" + plainToken;
        EmailContent content = EmailBuilder.build(
                "본인 경고 메일",
                "링크를 눌러 이 이메일 주소를 본인 경고 메일 수신처로 확인해 주세요.",
                expiresAt.atZone(ZoneId.systemDefault()),
                secureLink,
                appProperties.getContactEmail()
        );
        // 발송은 EmailOutboxScheduler가 비동기로 처리한다 (실제 발송 결과는 "이메일 발송 감사" 화면에서 확인)
        emailOutboxService.enqueue(plan, null, EmailType.WAITING_PERIOD_WARNING, request.email(), content);

        return SelfWarningEmailResponse.of(plan, true);
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
}
