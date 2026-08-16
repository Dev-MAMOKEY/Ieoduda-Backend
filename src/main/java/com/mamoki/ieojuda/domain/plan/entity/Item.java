package com.mamoki.ieojuda.domain.plan.entity;

import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long itemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id", nullable = true) // issue #62 - role_assigness.assigne_id 오탈자 정리
    private Recipient recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "life_id", nullable = false)
    private LifeArea lifeArea;

    @Column(name = "target_name", length = 100)
    private String targetName;

    @Column(name = "location_type", length = 100)
    private String locationType;

    @Column(name = "action", length = 2000)
    private String action;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "content", length = 2000)
    private String content;

    @Column(name = "precondition", length = 2000)
    private String precondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "disclosure_scope", length = 30)
    private DisclosureScope disclosureScope;

    @Column(name = "source_excerpt", length = 2000)
    private String sourceExcerpt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    private ItemStatus status;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "sort_order")
    private Integer sortOrder;

    // "실행 순서 점검" 화면 - 삭제형 항목이 인계형 항목보다 먼저 오면 순서 충돌로 판정하기 위한 분류
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", length = 30)
    private ItemActionType actionType;

    @Builder
    public Item(LifeArea lifeArea, String targetName, String locationType, String action, String title, String content,
                String precondition, DisclosureScope disclosureScope, String sourceExcerpt,
                Integer sortOrder, ItemActionType actionType) {
        this.lifeArea = lifeArea;
        this.targetName = targetName;
        this.locationType = locationType;
        this.action = action;
        this.title = title;
        this.content = content;
        this.precondition = precondition;
        this.disclosureScope = disclosureScope;
        this.sourceExcerpt = sourceExcerpt;
        this.sortOrder = sortOrder;
        this.actionType = actionType != null ? actionType : ItemActionType.OTHER;
        this.status = ItemStatus.PROPOSED;
    }

    public void approve() {
        this.status = ItemStatus.APPROVED;
        this.reviewedAt = LocalDateTime.now();
    }

    // 대화창 인라인 "수정" 버튼 - AI가 만든 초안을 사용자가 직접 고침
    public void updateContent(String targetName, String locationType, String action, String title, String content,
                               String precondition, DisclosureScope disclosureScope, ItemActionType actionType) {
        this.targetName = targetName;
        this.locationType = locationType;
        this.action = action;
        this.title = title;
        this.content = content;
        this.precondition = precondition;
        this.disclosureScope = disclosureScope;
        this.actionType = actionType != null ? actionType : ItemActionType.OTHER;
    }

    // 드래그로 실행 순서 변경
    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void assignRecipient(Recipient recipient) {
        this.recipient = recipient;
    }
}