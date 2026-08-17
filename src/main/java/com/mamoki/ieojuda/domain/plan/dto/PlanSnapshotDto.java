package com.mamoki.ieojuda.domain.plan.dto;

import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.plan.entity.ItemActionType;
import com.mamoki.ieojuda.domain.plan.entity.ItemStatus;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.entity.RoleType;

import java.util.UUID;
import java.util.List;

// 사망 신고가 확정되어 사건(ReleaseCase)이 열리는 순간의 계획 상태를 그대로 얼려 둔 스냅샷.
// 이 시점 이후 사용자가 계획을 수정하더라도, 이미 열린 사건의 검토·발송 로직은 이 스냅샷만 읽는다(issue #42).
public record PlanSnapshotDto(
        UUID planId,
        Integer waitingDays,
        List<ItemSnapshot> items,
        List<RecipientSnapshot> recipients
) {

    public record ItemSnapshot(
            UUID itemId,
            UUID recipientId,
            String targetName,
            String locationType,
            String action,
            String title,
            String content,
            String precondition,
            DisclosureScope disclosureScope,
            String sourceExcerpt,
            ItemStatus status,
            Integer sortOrder,
            ItemActionType actionType
    ) {
        public static ItemSnapshot from(Item item) {
            return new ItemSnapshot(
                    item.getItemId(),
                    item.getRecipient() != null ? item.getRecipient().getAssigneeId() : null,
                    item.getTargetName(),
                    item.getLocationType(),
                    item.getAction(),
                    item.getTitle(),
                    item.getContent(),
                    item.getPrecondition(),
                    item.getDisclosureScope(),
                    item.getSourceExcerpt(),
                    item.getStatus(),
                    item.getSortOrder(),
                    item.getActionType()
            );
        }
    }

    public record RecipientSnapshot(
            UUID assigneeId,
            String name,
            String email,
            RoleType roleType,
            Boolean isBackup,
            DisclosureScope disclosureScope,
            Integer maxWaitHours,
            UUID backupForId
    ) {
        public static RecipientSnapshot from(Recipient recipient) {
            return new RecipientSnapshot(
                    recipient.getAssigneeId(),
                    recipient.getName(),
                    recipient.getEmail(),
                    recipient.getRoleType(),
                    recipient.getIsBackup(),
                    recipient.getDisclosureScope(),
                    recipient.getMaxWaitHours(),
                    recipient.getBackupFor() != null ? recipient.getBackupFor().getAssigneeId() : null
            );
        }
    }

    public static PlanSnapshotDto of(Plan plan, List<Item> items, List<Recipient> recipients) {
        return new PlanSnapshotDto(
                plan.getPlanId(),
                plan.getWaitingDays(),
                items.stream().map(ItemSnapshot::from).toList(),
                recipients.stream().map(RecipientSnapshot::from).toList()
        );
    }
}
