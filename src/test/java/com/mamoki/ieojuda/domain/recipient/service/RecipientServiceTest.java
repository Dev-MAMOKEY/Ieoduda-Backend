package com.mamoki.ieojuda.domain.recipient.service;

import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.plan.service.PlanOwnershipReader;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.domain.securitytoken.service.SecurityTokenService;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.outbox.EmailOutboxService;
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

// 버그 회귀 방지 - 계획을 비활성화("삭제")한 뒤에도 "역할 담당자 상세 조회"가 예전처럼 그대로
// 동작하던 문제. findOwnedActivePlan()이 담당자 조회보다 먼저 막아야 한다.
class RecipientServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final UUID ASSIGNEE_ID = UUID.randomUUID();

    private ItemRepository itemRepository;
    private RecipientRepository recipientRepository;
    private PlanOwnershipReader planOwnershipReader;
    private EmailOutboxService emailOutboxService;
    private AppProperties appProperties;
    private SecurityTokenService securityTokenService;
    private RecipientService recipientService;

    @BeforeEach
    void setUp() {
        itemRepository = mock(ItemRepository.class);
        recipientRepository = mock(RecipientRepository.class);
        planOwnershipReader = mock(PlanOwnershipReader.class);
        emailOutboxService = mock(EmailOutboxService.class);
        appProperties = mock(AppProperties.class);
        securityTokenService = mock(SecurityTokenService.class);
        recipientService = new RecipientService(
                itemRepository, recipientRepository, planOwnershipReader, emailOutboxService,
                appProperties, securityTokenService);
    }

    @Test
    void getRecipient_whenPlanDeactivated_throwsAndNeverQueriesRecipient() {
        doThrow(new CustomException(ErrorCode.PLAN_DEACTIVATED))
                .when(planOwnershipReader).findOwnedActivePlan(USER_ID, PLAN_ID);

        assertThatThrownBy(() -> recipientService.getRecipient(USER_ID, PLAN_ID, ASSIGNEE_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PLAN_DEACTIVATED));

        verify(recipientRepository, never()).findById(any());
        verify(itemRepository, never()).findByRecipient_AssigneeIdOrderByItemIdAsc(any());
    }
}
