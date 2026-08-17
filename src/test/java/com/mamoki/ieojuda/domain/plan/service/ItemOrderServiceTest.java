package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.plan.dto.ItemReorderRequest;
import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.plan.entity.ItemActionType;
import com.mamoki.ieojuda.domain.plan.entity.ItemSemanticType;
import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.entity.RoleType;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// issue #90 완료 조건 - "명세서의 결정론적 규칙 6종이 모두 검사된다".
// 각 테스트는 검증 대상 규칙 하나만 걸리도록, 기본 픽스처(recipientA/B는 서로 대체 담당자로 등록되어
// 있고 50:50으로 항목을 나눠 맡음)를 "아무 규칙도 안 걸리는 깨끗한 상태"로 잡아두고 시작한다.
class ItemOrderServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();

    private PlanOwnershipReader planOwnershipReader;
    private ItemRepository itemRepository;
    private RecipientRepository recipientRepository;
    private ItemOrderService itemOrderService;

    private Plan plan;
    private Recipient recipientA;
    private Recipient recipientB;

    @BeforeEach
    void setUp() {
        planOwnershipReader = mock(PlanOwnershipReader.class);
        itemRepository = mock(ItemRepository.class);
        recipientRepository = mock(RecipientRepository.class);
        itemOrderService = new ItemOrderService(planOwnershipReader, itemRepository, recipientRepository);

        plan = mock(Plan.class);
        when(planOwnershipReader.findOwnedPlan(USER_ID, PLAN_ID)).thenReturn(plan);

        recipientA = buildRecipient("이지수");
        recipientB = buildRecipient("김민수");
        // 서로가 서로의 대체 담당자 - "대체 담당자 부재" 규칙이 기본 상태에서는 걸리지 않도록
        Recipient backupOfA = buildBackupRecipient("대체A", recipientA);
        Recipient backupOfB = buildBackupRecipient("대체B", recipientB);
        when(recipientRepository.findByPlan_PlanId(PLAN_ID))
                .thenReturn(List.of(recipientA, recipientB, backupOfA, backupOfB));
    }

    private Recipient buildRecipient(String name) {
        Recipient recipient = Recipient.builder().plan(plan).lifeArea(mock(LifeArea.class)).name(name)
                .email(name + "@test.com").roleType(RoleType.RELATIONSHIP_MANAGER).isBackup(false)
                .disclosureScope(DisclosureScope.RELATIONSHIP).maxWaitHours(168).backupFor(null).build();
        setField(recipient, "assigneeId", UUID.randomUUID());
        return recipient;
    }

    private Recipient buildBackupRecipient(String name, Recipient backupFor) {
        Recipient recipient = Recipient.builder().plan(plan).lifeArea(mock(LifeArea.class)).name(name)
                .email(name + "@test.com").roleType(RoleType.RELATIONSHIP_MANAGER).isBackup(true)
                .disclosureScope(DisclosureScope.RELATIONSHIP).maxWaitHours(168).backupFor(backupFor).build();
        setField(recipient, "assigneeId", UUID.randomUUID());
        return recipient;
    }

    private Item buildItem(Recipient recipient, String locationType, ItemActionType actionType,
                            ItemSemanticType semanticType, int sortOrder) {
        Item item = Item.builder()
                .lifeArea(mock(LifeArea.class)).targetName("대상").locationType(locationType)
                .action("행동").title("제목-" + sortOrder).content("내용")
                .disclosureScope(DisclosureScope.RELATIONSHIP).sourceExcerpt("근거")
                .sortOrder(sortOrder).actionType(actionType).semanticType(semanticType).build();
        if (recipient != null) {
            item.assignRecipient(recipient);
        }
        setField(item, "itemId", UUID.randomUUID());
        return item;
    }

    // itemId/assigneeId는 @GeneratedValue라 영속화 전에는 null이므로 테스트에서 직접 채운다
    private void setField(Object entity, String fieldName, Object value) {
        try {
            Field field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void stubItems(Item... items) {
        when(itemRepository.findByLifeArea_Plan_PlanIdOrderBySortOrderAscItemIdAsc(PLAN_ID)).thenReturn(List.of(items));
    }

    @Test
    void getOrderCheck_whenCloudDeleteBeforeFilePreserveInSameLocation_flagsBothAsConflict() {
        Item cloudDelete = buildItem(recipientA, "구글드라이브", ItemActionType.DELETE, ItemSemanticType.CLOUD_DELETE, 0);
        Item filePreserve = buildItem(recipientB, "구글드라이브", ItemActionType.TRANSFER, ItemSemanticType.FILE_PRESERVE, 1);
        stubItems(cloudDelete, filePreserve);

        var response = itemOrderService.getOrderCheck(USER_ID, PLAN_ID);

        assertThat(response.items()).allSatisfy(item -> assertThat(item.conflict()).isTrue());
    }

    @Test
    void getOrderCheck_whenChannelCloseBeforeClientNotifyInSameLocation_flagsBothAsConflict() {
        Item channelClose = buildItem(recipientA, "거래처 이메일", ItemActionType.DELETE, ItemSemanticType.CHANNEL_CLOSE, 0);
        Item clientNotify = buildItem(recipientB, "거래처 이메일", ItemActionType.TRANSFER, ItemSemanticType.CLIENT_NOTIFY, 1);
        stubItems(channelClose, clientNotify);

        var response = itemOrderService.getOrderCheck(USER_ID, PLAN_ID);

        assertThat(response.items()).allSatisfy(item -> assertThat(item.conflict()).isTrue());
    }

    @Test
    void getOrderCheck_whenDeviceResetBeforeRecordExportInSameLocation_flagsBothAsConflict() {
        Item deviceReset = buildItem(recipientA, "회사 노트북", ItemActionType.DELETE, ItemSemanticType.DEVICE_RESET, 0);
        Item recordExport = buildItem(recipientB, "회사 노트북", ItemActionType.TRANSFER, ItemSemanticType.RECORD_EXPORT, 1);
        stubItems(deviceReset, recordExport);

        var response = itemOrderService.getOrderCheck(USER_ID, PLAN_ID);

        assertThat(response.items()).allSatisfy(item -> assertThat(item.conflict()).isTrue());
    }

    @Test
    void getOrderCheck_whenLaterTypeComesFirst_noConflict() {
        Item filePreserve = buildItem(recipientA, "구글드라이브", ItemActionType.TRANSFER, ItemSemanticType.FILE_PRESERVE, 0);
        Item cloudDelete = buildItem(recipientB, "구글드라이브", ItemActionType.DELETE, ItemSemanticType.CLOUD_DELETE, 1);
        stubItems(filePreserve, cloudDelete);

        var response = itemOrderService.getOrderCheck(USER_ID, PLAN_ID);

        assertThat(response.items()).allSatisfy(item -> assertThat(item.conflict()).isFalse());
    }

    // 같은 semanticType 쌍이라도 locationType이 다르면(서로 무관한 계정) 충돌로 묶이지 않아야 한다
    @Test
    void getOrderCheck_whenSameSemanticPairButDifferentLocationType_noConflict() {
        Item cloudDelete = buildItem(recipientA, "인스타그램", ItemActionType.DELETE, ItemSemanticType.CLOUD_DELETE, 0);
        Item filePreserve = buildItem(recipientB, "구글드라이브", ItemActionType.TRANSFER, ItemSemanticType.FILE_PRESERVE, 1);
        stubItems(cloudDelete, filePreserve);

        var response = itemOrderService.getOrderCheck(USER_ID, PLAN_ID);

        assertThat(response.items()).allSatisfy(item -> assertThat(item.conflict()).isFalse());
    }

    @Test
    void getOrderCheck_whenItemsHaveNoSemanticType_noConflict() {
        Item first = buildItem(recipientA, "인스타그램", ItemActionType.DELETE, null, 0);
        Item second = buildItem(recipientB, "인스타그램", ItemActionType.TRANSFER, null, 1);
        stubItems(first, second);

        var response = itemOrderService.getOrderCheck(USER_ID, PLAN_ID);

        assertThat(response.items()).allSatisfy(item -> assertThat(item.conflict()).isFalse());
    }

    // 규칙 4 - 담당자 누락
    @Test
    void getOrderCheck_whenItemHasNoRecipient_flagsMissingRecipient() {
        Item unassigned = buildItem(null, "인스타그램", ItemActionType.OTHER, null, 0);
        Item assigned = buildItem(recipientA, "트위터", ItemActionType.OTHER, null, 1);
        stubItems(unassigned, assigned);

        var response = itemOrderService.getOrderCheck(USER_ID, PLAN_ID);

        var unassignedResult = response.items().stream().filter(i -> i.itemId().equals(unassigned.getItemId())).findFirst().orElseThrow();
        var assignedResult = response.items().stream().filter(i -> i.itemId().equals(assigned.getItemId())).findFirst().orElseThrow();
        assertThat(unassignedResult.conflict()).isTrue();
        assertThat(unassignedResult.conflictMessage()).contains("담당자가 배정되지 않았어요");
        assertThat(unassignedResult.recipientName()).isNull();
        assertThat(assignedResult.conflict()).isFalse();
    }

    // 규칙 6 - 대체 담당자 부재
    @Test
    void getOrderCheck_whenRecipientHasNoBackup_flagsMissingBackup() {
        Recipient noBackupRecipient = buildRecipient("박서준");
        Recipient hasBackupRecipient = buildRecipient("최유진");
        Recipient backupOfHasBackup = buildBackupRecipient("대체최유진", hasBackupRecipient);
        // 이 테스트만의 담당자 구성으로 교체 - 박서준은 대체 담당자가 없고, 최유진은 있음
        when(recipientRepository.findByPlan_PlanId(PLAN_ID))
                .thenReturn(List.of(noBackupRecipient, hasBackupRecipient, backupOfHasBackup));
        Item withoutBackup = buildItem(noBackupRecipient, "인스타그램", ItemActionType.OTHER, null, 0);
        Item withBackup = buildItem(hasBackupRecipient, "트위터", ItemActionType.OTHER, null, 1);
        stubItems(withoutBackup, withBackup);

        var response = itemOrderService.getOrderCheck(USER_ID, PLAN_ID);

        var withoutBackupResult = response.items().stream().filter(i -> i.itemId().equals(withoutBackup.getItemId())).findFirst().orElseThrow();
        var withBackupResult = response.items().stream().filter(i -> i.itemId().equals(withBackup.getItemId())).findFirst().orElseThrow();
        assertThat(withoutBackupResult.conflict()).isTrue();
        assertThat(withoutBackupResult.conflictMessage()).contains("대체 담당자가 없어요");
        assertThat(withBackupResult.conflict()).isFalse();
    }

    // 규칙 5 - 권한 집중 (전체 항목의 과반을 한 담당자가 맡으면 경고)
    @Test
    void getOrderCheck_whenOneRecipientHandlesMoreThanHalf_flagsAuthorityConcentration() {
        Item first = buildItem(recipientA, "인스타그램", ItemActionType.OTHER, null, 0);
        Item second = buildItem(recipientA, "트위터", ItemActionType.OTHER, null, 1);
        Item third = buildItem(recipientA, "페이스북", ItemActionType.OTHER, null, 2);
        Item fourth = buildItem(recipientB, "링크드인", ItemActionType.OTHER, null, 3);
        stubItems(first, second, third, fourth);

        var response = itemOrderService.getOrderCheck(USER_ID, PLAN_ID);

        // recipientA가 4개 중 3개(75% > 50%) 담당 - 3개 전부 권한 집중으로 표시, recipientB의 1개는 정상
        long concentrationFlagged = response.items().stream()
                .filter(item -> item.conflictMessage() != null && item.conflictMessage().contains("절반이 넘게 몰려"))
                .count();
        assertThat(concentrationFlagged).isEqualTo(3);
        var fourthResult = response.items().stream().filter(i -> i.itemId().equals(fourth.getItemId())).findFirst().orElseThrow();
        assertThat(fourthResult.conflict()).isFalse();
    }

    // 정확히 50:50이면 "과반 초과"가 아니므로 권한 집중이 아니다
    @Test
    void getOrderCheck_whenExactlyHalfEach_noAuthorityConcentrationConflict() {
        Item first = buildItem(recipientA, "인스타그램", ItemActionType.OTHER, null, 0);
        Item second = buildItem(recipientB, "트위터", ItemActionType.OTHER, null, 1);
        stubItems(first, second);

        var response = itemOrderService.getOrderCheck(USER_ID, PLAN_ID);

        assertThat(response.items()).allSatisfy(item -> assertThat(item.conflict()).isFalse());
    }

    @Test
    void confirm_whenConflictExists_throwsItemOrderConflict() {
        Item cloudDelete = buildItem(recipientA, "구글드라이브", ItemActionType.DELETE, ItemSemanticType.CLOUD_DELETE, 0);
        Item filePreserve = buildItem(recipientB, "구글드라이브", ItemActionType.TRANSFER, ItemSemanticType.FILE_PRESERVE, 1);
        stubItems(cloudDelete, filePreserve);

        assertThatThrownBy(() -> itemOrderService.confirm(USER_ID, PLAN_ID))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ITEM_ORDER_CONFLICT));
    }

    @Test
    void reorder_whenAllItemIdsProvided_updatesSortOrderAndResetsConfirmation() {
        Item first = buildItem(recipientA, "인스타그램", ItemActionType.OTHER, null, 0);
        Item second = buildItem(recipientB, "트위터", ItemActionType.OTHER, null, 1);
        stubItems(first, second);

        itemOrderService.reorder(USER_ID, PLAN_ID, new ItemReorderRequest(List.of(second.getItemId(), first.getItemId())));

        org.mockito.Mockito.verify(plan).resetOrderConfirmation();
    }
}
