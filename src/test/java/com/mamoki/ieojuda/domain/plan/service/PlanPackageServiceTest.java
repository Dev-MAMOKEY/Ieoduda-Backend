package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.entity.Relationship;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.plan.entity.ItemActionType;
import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanStatus;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.entity.RoleType;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// issue #81/#90 - 역할별 패키지 미리보기 및 작성자 봉인. 봉인 차단 검사 6가지
// (확인자수/작성자 이름/금지정보/근거/권한집중/실행순서확정)를 순서대로 검증한다.
// issue #93 - 작성자 이름이 없으면 PartnerReviewResponse.targetName이 null이 되므로 봉인 시점에 차단한다.
class PlanPackageServiceTest {

    private PlanOwnershipReader planOwnershipReader;
    private ItemRepository itemRepository;
    private ConfirmerRepository confirmerRepository;
    private PlanPackageService planPackageService;

    private Plan plan;
    private com.mamoki.ieojuda.domain.account.entity.User user;
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        planOwnershipReader = mock(PlanOwnershipReader.class);
        itemRepository = mock(ItemRepository.class);
        confirmerRepository = mock(ConfirmerRepository.class);
        planPackageService = new PlanPackageService(planOwnershipReader, itemRepository, confirmerRepository);

        user = mock(com.mamoki.ieojuda.domain.account.entity.User.class);
        when(user.getName()).thenReturn("김나무");
        plan = Plan.builder().user(user).build();
        when(planOwnershipReader.findOwnedPlan(USER_ID, PLAN_ID)).thenReturn(plan);
    }

    private Recipient buildRecipient(UUID assigneeId, String name) {
        Recipient recipient = Recipient.builder()
                .plan(plan).lifeArea(mock(LifeArea.class)).name(name).email(name + "@test.com")
                .roleType(RoleType.RELATIONSHIP_MANAGER).isBackup(false)
                .disclosureScope(DisclosureScope.RELATIONSHIP).maxWaitHours(168).backupFor(null).build();
        setId(recipient, assigneeId);
        return recipient;
    }

    private Item buildItem(Recipient recipient, String title, String content, String precondition, String sourceExcerpt) {
        return buildItem(recipient, "인스타그램 아이디로 SNS 계정을 정리해줘", title, content, precondition, sourceExcerpt);
    }

    private Item buildItem(Recipient recipient, String action, String title, String content, String precondition, String sourceExcerpt) {
        Item item = Item.builder()
                .lifeArea(mock(LifeArea.class)).targetName("대상").locationType("위치").action(action)
                .title(title).content(content).precondition(precondition)
                .disclosureScope(DisclosureScope.RELATIONSHIP).sourceExcerpt(sourceExcerpt)
                .sortOrder(0).actionType(ItemActionType.OTHER).build();
        item.assignRecipient(recipient);
        return item;
    }

    private void setId(Object entity, UUID id) {
        try {
            var field = entity.getClass().getDeclaredField("assigneeId");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void stubAcceptedConfirmers(int count) {
        List<Confirmer> confirmers = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            Confirmer confirmer = Confirmer.builder().plan(plan).name("c" + i)
                    .relationship(Relationship.FRIEND).email("c" + i + "@test.com").build();
            confirmer.accept(null);
            confirmers.add(confirmer);
        }
        when(confirmerRepository.findByPlan_PlanIdOrderByConfirmIdAsc(PLAN_ID)).thenReturn(confirmers);
    }

    @Test
    void getPreview_groupsActionsByRecipient_excludesOtherRolesImplicitly() {
        Recipient r1 = buildRecipient(UUID.randomUUID(), "이지수");
        Recipient r2 = buildRecipient(UUID.randomUUID(), "김민수");
        Item item1 = buildItem(r1, "SNS 정리", "비공개 전환", "", "근거1");
        Item item2 = buildItem(r2, "메일 정리", "이전", "", "근거2");
        when(itemRepository.findByLifeArea_Plan_PlanIdAndRecipientIsNotNullOrderBySortOrderAscItemIdAsc(PLAN_ID))
                .thenReturn(List.of(item1, item2));

        var response = planPackageService.getPreview(USER_ID, PLAN_ID);

        assertThat(response.packages()).hasSize(2);
        assertThat(response.packages().get(0).recipientName()).isEqualTo("이지수");
        assertThat(response.packages().get(0).actions()).hasSize(1);
        assertThat(response.packages().get(0).actions().get(0).title()).isEqualTo("SNS 정리");
        // 노션 "역할별 패키지 미리보기" 표시 항목: 대상/위치유형/행동/선행조건/제외정보 - "행동"(action) 포함 확인
        assertThat(response.packages().get(0).actions().get(0).action())
                .isEqualTo("인스타그램 아이디로 SNS 계정을 정리해줘");
        assertThat(response.packages().get(0).excluded()).contains("다른 역할의 항목", "자격증명");
    }

    @Test
    void seal_whenActionFieldContainsCredentialValue_throwsPackageSealBlocked() {
        stubAcceptedConfirmers(2);
        Recipient r1 = buildRecipient(UUID.randomUUID(), "이지수");
        // title/content는 정상이지만 action(수행해야 할 행동 전체 설명)에 자격증명이 섞인 경우도 잡아야 한다
        Item item = buildItem(r1, "인스타그램 비밀번호는 abcd1234야", "계정 정리", "비공개 전환", "", "근거");
        when(itemRepository.findByLifeArea_Plan_PlanIdAndRecipientIsNotNullOrderBySortOrderAscItemIdAsc(PLAN_ID))
                .thenReturn(List.of(item));

        assertThatThrownBy(() -> planPackageService.seal(USER_ID, PLAN_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PACKAGE_SEAL_BLOCKED));
    }

    @Test
    void seal_whenFewerThanTwoAcceptedConfirmers_throwsInsufficientConfirmers() {
        stubAcceptedConfirmers(1);

        assertThatThrownBy(() -> planPackageService.seal(USER_ID, PLAN_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_CONFIRMERS));
        verify(itemRepository, never()).findByLifeArea_Plan_PlanIdAndRecipientIsNotNullOrderBySortOrderAscItemIdAsc(any());
    }

    @Test
    void seal_whenAuthorNameIsBlank_throwsPackageSealBlocked() {
        stubAcceptedConfirmers(2);
        when(user.getName()).thenReturn(null);

        assertThatThrownBy(() -> planPackageService.seal(USER_ID, PLAN_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PACKAGE_SEAL_BLOCKED));
        verify(itemRepository, never()).findByLifeArea_Plan_PlanIdAndRecipientIsNotNullOrderBySortOrderAscItemIdAsc(any());
    }

    @Test
    void seal_whenItemContainsCredentialValue_throwsPackageSealBlocked() {
        stubAcceptedConfirmers(2);
        Recipient r1 = buildRecipient(UUID.randomUUID(), "이지수");
        Item item = buildItem(r1, "계정 정리", "비밀번호는 abcd1234", "", "근거");
        when(itemRepository.findByLifeArea_Plan_PlanIdAndRecipientIsNotNullOrderBySortOrderAscItemIdAsc(PLAN_ID))
                .thenReturn(List.of(item));

        assertThatThrownBy(() -> planPackageService.seal(USER_ID, PLAN_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PACKAGE_SEAL_BLOCKED));
        assertThat(plan.getStatus()).isNotEqualTo(PlanStatus.SEALED);
    }

    @Test
    void seal_whenItemMissingSourceExcerpt_throwsPackageSealBlocked() {
        stubAcceptedConfirmers(2);
        Recipient r1 = buildRecipient(UUID.randomUUID(), "이지수");
        Item item = buildItem(r1, "계정 정리", "비공개 전환", "", "");
        when(itemRepository.findByLifeArea_Plan_PlanIdAndRecipientIsNotNullOrderBySortOrderAscItemIdAsc(PLAN_ID))
                .thenReturn(List.of(item));

        assertThatThrownBy(() -> planPackageService.seal(USER_ID, PLAN_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PACKAGE_SEAL_BLOCKED));
    }

    // issue #90 - 한 담당자가 전체 항목의 과반(50% 초과)을 맡으면 봉인 차단
    @Test
    void seal_whenOneRecipientHandlesMoreThanHalf_throwsPackageSealBlocked() {
        stubAcceptedConfirmers(2);
        Recipient r1 = buildRecipient(UUID.randomUUID(), "이지수");
        Recipient r2 = buildRecipient(UUID.randomUUID(), "김민수");
        Item item1 = buildItem(r1, "계정1 정리", "비공개 전환", "", "근거1");
        Item item2 = buildItem(r1, "계정2 정리", "비공개 전환", "", "근거2");
        Item item3 = buildItem(r2, "계정3 정리", "비공개 전환", "", "근거3");
        when(itemRepository.findByLifeArea_Plan_PlanIdAndRecipientIsNotNullOrderBySortOrderAscItemIdAsc(PLAN_ID))
                .thenReturn(List.of(item1, item2, item3));

        assertThatThrownBy(() -> planPackageService.seal(USER_ID, PLAN_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PACKAGE_SEAL_BLOCKED));
        assertThat(plan.getStatus()).isNotEqualTo(PlanStatus.SEALED);
    }

    // issue #90 - 실행 순서가 아직 확정(orderConfirmedAt != null)되지 않았으면 봉인 차단
    @Test
    void seal_whenOrderNotConfirmed_throwsOrderNotConfirmed() {
        stubAcceptedConfirmers(2);
        Recipient r1 = buildRecipient(UUID.randomUUID(), "이지수");
        Item item = buildItem(r1, "계정 정리", "비공개 전환", "", "지수에게 SNS 정리를 부탁");
        when(itemRepository.findByLifeArea_Plan_PlanIdAndRecipientIsNotNullOrderBySortOrderAscItemIdAsc(PLAN_ID))
                .thenReturn(List.of(item));

        assertThatThrownBy(() -> planPackageService.seal(USER_ID, PLAN_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_CONFIRMED));
        assertThat(plan.getStatus()).isNotEqualTo(PlanStatus.SEALED);
    }

    @Test
    void seal_whenAllChecksPass_sealsThePlan() {
        stubAcceptedConfirmers(2);
        Recipient r1 = buildRecipient(UUID.randomUUID(), "이지수");
        Item item = buildItem(r1, "계정 정리", "비공개 전환", "", "지수에게 SNS 정리를 부탁");
        when(itemRepository.findByLifeArea_Plan_PlanIdAndRecipientIsNotNullOrderBySortOrderAscItemIdAsc(PLAN_ID))
                .thenReturn(List.of(item));
        plan.confirmOrder();

        var response = planPackageService.seal(USER_ID, PLAN_ID);

        assertThat(response.status()).isEqualTo("SEALED");
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.SEALED);
    }
}
