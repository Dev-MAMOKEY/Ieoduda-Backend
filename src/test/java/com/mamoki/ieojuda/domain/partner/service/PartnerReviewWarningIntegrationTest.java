package com.mamoki.ieojuda.domain.partner.service;

import com.mamoki.ieojuda.domain.account.entity.AdminPermission;
import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.entity.UserRole;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.audit.entity.EmailDeliveryStatus;
import com.mamoki.ieojuda.domain.audit.entity.EmailLog;
import com.mamoki.ieojuda.domain.audit.entity.EmailType;
import com.mamoki.ieojuda.domain.audit.repository.EmailLogRepository;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.entity.DisputeContact;
import com.mamoki.ieojuda.domain.confirmer.entity.Relationship;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.confirmer.repository.DisputeContactRepository;
import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import com.mamoki.ieojuda.domain.evidence.entity.EvidenceType;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceRepository;
import com.mamoki.ieojuda.domain.partner.dto.PartnerReviewDecisionRequest;
import com.mamoki.ieojuda.domain.partner.entity.PartnerReviewer;
import com.mamoki.ieojuda.domain.partner.repository.PartnerReviewerRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanVersion;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanVersionRepository;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCaseStatus;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.securitytoken.repository.SecurityTokenRepository;
import com.mamoki.ieojuda.global.email.contract.BounceType;
import com.mamoki.ieojuda.global.email.contract.EmailFaill;
import com.mamoki.ieojuda.global.email.contract.EmailSendResult;
import com.mamoki.ieojuda.global.email.sender.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

// 완료 조건 통합 검증 - "증빙 승인 및 대기 시작 시 이의 연락처 경고 발송 연결"과 "대기 시작 전에 경고가
// 전송된다"를 실제 스프링 컨텍스트·DB로 검증한다. PartnerReviewAccessHttpIntegrationTest와 동일하게
// SMTP만 @MockitoBean으로 격리한다.
@SpringBootTest
class PartnerReviewWarningIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlanRepository planRepository;
    @Autowired
    private PlanVersionRepository planVersionRepository;
    @Autowired
    private ReleaseCaseRepository releaseCaseRepository;
    @Autowired
    private PartnerReviewerRepository partnerReviewerRepository;
    @Autowired
    private EvidenceRepository evidenceRepository;
    @Autowired
    private ConfirmerRepository confirmerRepository;
    @Autowired
    private DisputeContactRepository disputeContactRepository;
    @Autowired
    private EmailLogRepository emailLogRepository;
    @Autowired
    private PartnerReviewService partnerReviewService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private SecurityTokenRepository securityTokenRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private EmailSender emailSender;

    private User owner;
    private Plan plan;
    private PlanVersion planVersion;
    private ReleaseCase releaseCase;
    private Confirmer confirmerA;
    private Confirmer confirmerB;
    private Evidence evidenceA;
    private Evidence evidenceB;
    private DisputeContact disputeContact;
    private User reviewerUser;

    @BeforeEach
    void setUp() {
        owner = userRepository.saveAndFlush(User.builder()
                .email("owner-" + UUID.randomUUID() + "@test.com").password("hash").name("작성자").build());
        plan = planRepository.saveAndFlush(Plan.builder().user(owner).build());
        plan.updateWaitingDays(7);
        plan = planRepository.saveAndFlush(plan);
        planVersion = planVersionRepository.saveAndFlush(
                PlanVersion.builder().plan(plan).versionNum(1).snapshotData("{}").build());
        releaseCase = releaseCaseRepository.saveAndFlush(ReleaseCase.builder().plan(plan).planVersion(planVersion).build());
        releaseCase.confirmReport();
        releaseCase.awaitEvidence();
        releaseCase.startEvidenceReview();
        releaseCase = releaseCaseRepository.saveAndFlush(releaseCase);

        // 매칭된 확인자 2명 - PartnerReviewService가 요구 승인 건수를 이 값으로 계산한다
        confirmerA = confirmerRepository.saveAndFlush(buildMatchedConfirmer("A"));
        confirmerB = confirmerRepository.saveAndFlush(buildMatchedConfirmer("B"));

        evidenceA = evidenceRepository.saveAndFlush(Evidence.builder()
                .confirmer(confirmerA).plan(plan).releaseCase(releaseCase)
                .storageKey("evidence/warning-test/a").fileName("a.pdf").mimeType("application/pdf")
                .integrityHash("hash-a").evidenceType(EvidenceType.DEATH_CERTIFICATE).build());
        evidenceB = evidenceRepository.saveAndFlush(Evidence.builder()
                .confirmer(confirmerB).plan(plan).releaseCase(releaseCase)
                .storageKey("evidence/warning-test/b").fileName("b.pdf").mimeType("application/pdf")
                .integrityHash("hash-b").evidenceType(EvidenceType.DEATH_CERTIFICATE).build());

        disputeContact = DisputeContact.builder().plan(plan)
                .email("dispute-" + UUID.randomUUID() + "@test.com").name("이의연락처").build();
        disputeContact.verify();
        disputeContact = disputeContactRepository.saveAndFlush(disputeContact);

        reviewerUser = User.builder().email("reviewer-" + UUID.randomUUID() + "@test.com")
                .password(passwordEncoder.encode("hash")).name("검토자").build();
        ReflectionTestUtils.setField(reviewerUser, "role", UserRole.EXTERNAL);
        ReflectionTestUtils.setField(reviewerUser, "permissions", Set.of(AdminPermission.EVIDENCE_REVIEW));
        reviewerUser = userRepository.saveAndFlush(reviewerUser);
        partnerReviewerRepository.saveAndFlush(PartnerReviewer.builder()
                .user(reviewerUser).name("검토자").email(reviewerUser.getEmail()).build());
    }

    private Confirmer buildMatchedConfirmer(String name) {
        Confirmer confirmer = Confirmer.builder().plan(plan).name(name).relationship(Relationship.FRIEND)
                .email("confirmer-warning-" + UUID.randomUUID() + "@test.com").build();
        confirmer.accept(null);
        confirmer.report(LocalDate.of(2026, 8, 15));
        confirmer.markMatched();
        return confirmer;
    }

    @AfterEach
    void tearDown() {
        // 이 사건에 묶인 RAISE_OBJECTION 토큰(이의 연락처 FK 참조)을 먼저 지워야 dispute_contacts/
        // release_cases 삭제 시 FK 위반이 나지 않는다 (DeathReportReadinessIntegrationTest와 동일 패턴).
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> securityTokenRepository.deleteByReleaseCase_CaseId(releaseCase.getCaseId()));
        // decide() 호출로 evidence/releaseCase의 버전이 바뀌었으므로, setUp() 시점의 stale 참조로
        // delete(entity)를 호출하면 낙관적 잠금 예외가 난다 - id 기반 삭제로 매번 새로 조회하게 한다.
        evidenceRepository.deleteById(evidenceA.getEvidenceId());
        evidenceRepository.deleteById(evidenceB.getEvidenceId());
        confirmerRepository.deleteById(confirmerA.getConfirmId());
        confirmerRepository.deleteById(confirmerB.getConfirmId());
        disputeContactRepository.deleteById(disputeContact.getContactId());
        partnerReviewerRepository.deleteAll(partnerReviewerRepository.findAll().stream()
                .filter(r -> r.getUser().getUserId().equals(reviewerUser.getUserId()))
                .toList());
        for (EmailLog log : emailLogRepository.findByPlan_PlanIdOrderByRequestedAtDesc(plan.getPlanId())) {
            emailLogRepository.deleteById(log.getLogId());
        }
        releaseCaseRepository.deleteById(releaseCase.getCaseId());
        planVersionRepository.deleteById(planVersion.getVersionId());
        planRepository.deleteById(plan.getPlanId());
        userRepository.deleteById(owner.getUserId());
        userRepository.deleteById(reviewerUser.getUserId());
    }

    private void approve(Evidence evidence) {
        partnerReviewService.decide(evidence.getEvidenceId(), reviewerUser.getUserId(),
                new PartnerReviewDecisionRequest(PartnerReviewDecisionRequest.PartnerReviewDecision.APPROVE, null, "hash"), null);
    }

    private EmailLog findObjectionWindowLog() {
        return emailLogRepository.findByPlan_PlanIdOrderByRequestedAtDesc(plan.getPlanId()).stream()
                .filter(log -> log.getEmailType() == EmailType.OBJECTION_WINDOW_NOTICE)
                .findFirst()
                .orElseThrow(() -> new AssertionError("OBJECTION_WINDOW_NOTICE 로그가 기록되지 않았습니다"));
    }

    @Test
    void decide_whenLastEvidenceApprovedAndWarningSendSucceeds_startsWaiting() {
        when(emailSender.send(eq(disputeContact.getEmail()), any())).thenReturn(EmailSendResult.success("msg-1"));

        approve(evidenceA);
        approve(evidenceB); // 매칭된 확인자 2명 전원 승인 - 대기 시작 트리거

        ReleaseCase updated = releaseCaseRepository.findById(releaseCase.getCaseId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ReleaseCaseStatus.WAITING);
        assertThat(updated.getFrozen()).isFalse();

        EmailLog log = findObjectionWindowLog();
        assertThat(log.getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
    }

    // 핵심 완료 조건 - 이의 연락처 경고 발송이 실패하면 WAITING으로 전이하지 않고, 실패가 성공으로
    // 기록되지도 않는다. 대신 사건이 동결되어 운영 검토로 전환된다.
    @Test
    void decide_whenLastEvidenceApprovedAndWarningSendFails_doesNotStartWaitingAndFreezesCase() {
        when(emailSender.send(eq(disputeContact.getEmail()), any()))
                .thenReturn(EmailSendResult.failure(BounceType.PERMANENT, EmailFaill.INVALID_ADDRESS_FORMAT));

        approve(evidenceA);
        approve(evidenceB);

        ReleaseCase updated = releaseCaseRepository.findById(releaseCase.getCaseId()).orElseThrow();
        assertThat(updated.getStatus()).isNotEqualTo(ReleaseCaseStatus.WAITING);
        assertThat(updated.getFrozen()).isTrue();

        EmailLog log = findObjectionWindowLog();
        assertThat(log.getStatus()).isEqualTo(EmailDeliveryStatus.FAILED);
    }
}
