package com.mamoki.ieojuda.domain.confirmer.service;

import com.mamoki.ieojuda.domain.confirmer.dto.DisputeContactRegisterRequest;
import com.mamoki.ieojuda.domain.confirmer.repository.DisputeContactRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.domain.plan.service.PlanOwnershipReader;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.sender.EmailSender;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.ratelimit.PublicLinkAuditor;
import com.mamoki.ieojuda.global.ratelimit.TokenLookupGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 사용자 B가 사용자 A의 planId에 이의 제기 연락처를 등록/수정하려 하면 PLAN_NOT_FOUND로 막혀야 한다.
// 통과시키면 공격자가 스스로를 피해자 계획의 이의 제기 연락처로 등록해 발송 거부권을 얻으므로, disputeContactRepository/emailSender에
// 전혀 손대지 않는지가 특히 중요하다.
class DisputeContactServiceBolaTest {

    private static final Long ATTACKER_ID = 2L;
    private static final Long PLAN_ID = 10L;
    private static final Long CONTACT_ID = 100L;

    private PlanRepository planRepository;
    private DisputeContactRepository disputeContactRepository;
    private EmailSender emailSender;
    private DisputeContactService disputeContactService;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        disputeContactRepository = mock(DisputeContactRepository.class);
        emailSender = mock(EmailSender.class);
        disputeContactService = new DisputeContactService(
                new PlanOwnershipReader(planRepository),
                disputeContactRepository,
                emailSender,
                mock(AppProperties.class),
                mock(TokenLookupGuard.class),
                mock(PublicLinkAuditor.class)
        );
        when(planRepository.findByPlanIdAndUser_UserId(PLAN_ID, ATTACKER_ID)).thenReturn(Optional.empty());
    }

    @Test
    void registerRejectsNonOwnerAndDoesNotRegisterAContact() {
        DisputeContactRegisterRequest request = new DisputeContactRegisterRequest("공격자", "attacker@example.com");

        assertThatThrownBy(() -> disputeContactService.register(ATTACKER_ID, PLAN_ID, request))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        verifyNoInteractions(disputeContactRepository);
        verifyNoInteractions(emailSender);
    }

    @Test
    void updateRejectsNonOwnerAndDoesNotTouchTheContact() {
        DisputeContactRegisterRequest request = new DisputeContactRegisterRequest("공격자", "attacker@example.com");

        assertThatThrownBy(() -> disputeContactService.update(ATTACKER_ID, PLAN_ID, CONTACT_ID, request))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        verifyNoInteractions(disputeContactRepository);
        verifyNoInteractions(emailSender);
    }
}
