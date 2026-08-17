package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.entity.DisputeContact;
import com.mamoki.ieojuda.domain.confirmer.entity.Relationship;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.confirmer.repository.DisputeContactRepository;
import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanStatus;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 사건 생성 직전 계획 준비도 검증 - 각 조건이 단독으로 막는지, 모두 충족하면 통과하는지 확인한다.
class PlanReadinessValidatorTest {

    private static final UUID PLAN_ID = UUID.randomUUID();

    private ConfirmerRepository confirmerRepository;
    private ItemRepository itemRepository;
    private DisputeContactRepository disputeContactRepository;
    private PlanReadinessValidator validator;

    @BeforeEach
    void setUp() {
        confirmerRepository = mock(ConfirmerRepository.class);
        itemRepository = mock(ItemRepository.class);
        disputeContactRepository = mock(DisputeContactRepository.class);
        validator = new PlanReadinessValidator(confirmerRepository, itemRepository, disputeContactRepository);
    }

    private Plan readyPlan() {
        Plan plan = mock(Plan.class);
        when(plan.getPlanId()).thenReturn(PLAN_ID);
        when(plan.getStatus()).thenReturn(PlanStatus.SEALED);
        when(plan.getWaitingDays()).thenReturn(14);
        when(plan.getSelfWarningEmailVerified()).thenReturn(true);
        when(plan.getOrderConfirmedAt()).thenReturn(LocalDateTime.now());
        return plan;
    }

    private void stubVerifiedDisputeContact() {
        DisputeContact contact = DisputeContact.builder().plan(mock(Plan.class)).email("dispute@test.com").name("이의").build();
        contact.verify();
        when(disputeContactRepository.findFirstByPlan_PlanIdOrderByContactIdDesc(PLAN_ID)).thenReturn(Optional.of(contact));
    }

    private void stubAssignedItems() {
        Item item = Item.builder().lifeArea(mock(LifeArea.class)).build();
        item.assignRecipient(mock(Recipient.class));
        when(itemRepository.findByLifeArea_Plan_PlanIdOrderBySortOrderAscItemIdAsc(PLAN_ID)).thenReturn(List.of(item));
    }

    private void stubTwoDistinctAcceptedConfirmers() {
        Confirmer a = Confirmer.builder().plan(mock(Plan.class)).name("A").relationship(Relationship.FRIEND).email("a@test.com").build();
        a.accept(null);
        Confirmer b = Confirmer.builder().plan(mock(Plan.class)).name("B").relationship(Relationship.FRIEND).email("b@test.com").build();
        b.accept(null);
        when(confirmerRepository.findByPlan_PlanIdOrderByConfirmIdAsc(PLAN_ID)).thenReturn(List.of(a, b));
    }

    private void stubAllConditionsExceptUnderTest() {
        stubVerifiedDisputeContact();
        stubAssignedItems();
        stubTwoDistinctAcceptedConfirmers();
    }

    @Test
    void validate_whenAllConditionsMet_doesNotThrow() {
        Plan plan = readyPlan();
        stubAllConditionsExceptUnderTest();

        assertThatCode(() -> validator.validate(plan)).doesNotThrowAnyException();
    }

    @Test
    void validate_whenPlanIsDraft_throwsPlanNotReady() {
        Plan plan = readyPlan();
        when(plan.getStatus()).thenReturn(PlanStatus.DRAFT);

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PLAN_NOT_READY);
    }

    @Test
    void validate_whenPlanIsDeactivated_throwsPlanNotReady() {
        Plan plan = readyPlan();
        when(plan.getStatus()).thenReturn(PlanStatus.DEACTIVATED);

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PLAN_NOT_READY);
    }

    @Test
    void validate_whenWaitingDaysNotSet_throwsWaitingPeriodNotSet() {
        Plan plan = readyPlan();
        when(plan.getWaitingDays()).thenReturn(null);

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WAITING_PERIOD_NOT_SET);
    }

    @Test
    void validate_whenSelfWarningEmailNotVerified_throwsSelfWarningEmailNotVerified() {
        Plan plan = readyPlan();
        when(plan.getSelfWarningEmailVerified()).thenReturn(false);

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SELF_WARNING_EMAIL_NOT_VERIFIED);
    }

    @Test
    void validate_whenNoDisputeContactRegistered_throwsDisputeContactNotVerified() {
        Plan plan = readyPlan();
        when(disputeContactRepository.findFirstByPlan_PlanIdOrderByContactIdDesc(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DISPUTE_CONTACT_NOT_VERIFIED);
    }

    @Test
    void validate_whenDisputeContactNotVerified_throwsDisputeContactNotVerified() {
        Plan plan = readyPlan();
        DisputeContact unverified = DisputeContact.builder().plan(mock(Plan.class)).email("dispute@test.com").name("이의").build();
        when(disputeContactRepository.findFirstByPlan_PlanIdOrderByContactIdDesc(PLAN_ID)).thenReturn(Optional.of(unverified));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DISPUTE_CONTACT_NOT_VERIFIED);
    }

    @Test
    void validate_whenItemHasNoAssignee_throwsItemAssigneeMissing() {
        Plan plan = readyPlan();
        stubVerifiedDisputeContact();
        Item unassigned = Item.builder().lifeArea(mock(LifeArea.class)).build();
        when(itemRepository.findByLifeArea_Plan_PlanIdOrderBySortOrderAscItemIdAsc(PLAN_ID)).thenReturn(List.of(unassigned));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_ASSIGNEE_MISSING);
    }

    @Test
    void validate_whenOrderNotConfirmed_throwsOrderNotConfirmed() {
        Plan plan = readyPlan();
        when(plan.getOrderConfirmedAt()).thenReturn(null);
        stubVerifiedDisputeContact();
        stubAssignedItems();

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_NOT_CONFIRMED);
    }

    @Test
    void validate_whenFewerThanTwoAcceptedConfirmers_throwsInsufficientConfirmers() {
        Plan plan = readyPlan();
        stubVerifiedDisputeContact();
        stubAssignedItems();
        Confirmer onlyOne = Confirmer.builder().plan(mock(Plan.class)).name("A").relationship(Relationship.FRIEND).email("a@test.com").build();
        onlyOne.accept(null);
        when(confirmerRepository.findByPlan_PlanIdOrderByConfirmIdAsc(PLAN_ID)).thenReturn(List.of(onlyOne));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_CONFIRMERS);
    }

    // 정규화(trim + lowercase) 후 같은 이메일이면 서로 다른 확인자로 세지 않는다
    @Test
    void validate_whenAcceptedConfirmersShareNormalizedEmail_throwsInsufficientConfirmers() {
        Plan plan = readyPlan();
        stubVerifiedDisputeContact();
        stubAssignedItems();
        Confirmer a = Confirmer.builder().plan(mock(Plan.class)).name("A").relationship(Relationship.FRIEND).email(" Same@Test.com ".trim()).build();
        a.accept(null);
        Confirmer b = Confirmer.builder().plan(mock(Plan.class)).name("B").relationship(Relationship.FRIEND).email("same@test.com").build();
        b.accept(null);
        when(confirmerRepository.findByPlan_PlanIdOrderByConfirmIdAsc(PLAN_ID)).thenReturn(List.of(a, b));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_CONFIRMERS);
    }
}
