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
    @JoinColumn(name = "assigne_id", nullable = true)
    private Recipient recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "life_id", nullable = false)
    private LifeArea lifeArea;

    @Column(name = "target_name", length = 100)
    private String targetName;

    @Column(name = "location_type", length = 100)
    private String locationType;

    @Column(name = "action", columnDefinition = "TEXT")
    private String action;

    @Column(name = "precondition", columnDefinition = "TEXT")
    private String precondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "disclosure_scope", length = 30)
    private DisclosureScope disclosureScope;

    @Column(name = "source_excerpt", columnDefinition = "TEXT")
    private String sourceExcerpt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    private ItemStatus status;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Builder
    public Item(LifeArea lifeArea, String targetName, String locationType, String action,
                String precondition, DisclosureScope disclosureScope, String sourceExcerpt,
                Integer sortOrder) {
        this.lifeArea = lifeArea;
        this.targetName = targetName;
        this.locationType = locationType;
        this.action = action;
        this.precondition = precondition;
        this.disclosureScope = disclosureScope;
        this.sourceExcerpt = sourceExcerpt;
        this.sortOrder = sortOrder;
        this.status = ItemStatus.PROPOSED;
    }

    public void approve() {
        this.status = ItemStatus.APPROVED;
        this.reviewedAt = LocalDateTime.now();
    }

    // 대화창 인라인 "수정" 버튼 - AI가 만든 초안을 사용자가 직접 고침
    public void updateContent(String targetName, String locationType, String action,
                               String precondition, DisclosureScope disclosureScope) {
        this.targetName = targetName;
        this.locationType = locationType;
        this.action = action;
        this.precondition = precondition;
        this.disclosureScope = disclosureScope;
    }

    public void assignRecipient(Recipient recipient) {
        this.recipient = recipient;
    }
}