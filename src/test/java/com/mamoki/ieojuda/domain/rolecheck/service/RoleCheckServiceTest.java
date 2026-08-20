package com.mamoki.ieojuda.domain.rolecheck.service;

import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.plan.service.PlanOwnershipReader;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 버그 회귀 방지 - 계획이 비활성화된 뒤에도 "역할 점검" 상단 이름 목록(담당자·확인자)이 그대로
// 조회되던 문제. findOwnedActivePlan()이 계획 조회 단계에서 먼저 막아야 한다.
class RoleCheckServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();

    private PlanOwnershipReader planOwnershipReader;
    private RecipientRepository recipientRepository;
    private ConfirmerRepository confirmerRepository;
    private RoleCheckService roleCheckService;

    @BeforeEach
    void setUp() {
        planOwnershipReader = mock(PlanOwnershipReader.class);
        recipientRepository = mock(RecipientRepository.class);
        confirmerRepository = mock(ConfirmerRepository.class);
        roleCheckService = new RoleCheckService(planOwnershipReader, recipientRepository, confirmerRepository);
    }

    @Test
    void getRoleChecks_whenPlanDeactivated_throwsAndNeverQueriesRecipientsOrConfirmers() {
        doThrow(new CustomException(ErrorCode.PLAN_DEACTIVATED))
                .when(planOwnershipReader).findOwnedActivePlan(USER_ID, PLAN_ID);

        assertThatThrownBy(() -> roleCheckService.getRoleChecks(USER_ID, PLAN_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PLAN_DEACTIVATED));

        verify(recipientRepository, never()).findByPlan_PlanIdAndIsBackupFalseOrderByAssigneeIdAsc(any());
        verify(confirmerRepository, never()).findByPlan_PlanIdOrderByConfirmIdAsc(any());
    }
}
