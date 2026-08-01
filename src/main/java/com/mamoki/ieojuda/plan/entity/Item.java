package com.mamoki.ieojuda.plan.entity;

import com.mamoki.ieojuda.recipient.entity.Recipient;
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

    @Builder
    public Item(LifeArea lifeArea, String locationType, String action,
                String precondition, DisclosureScope disclosureScope, String sourceExcerpt) {
        this.lifeArea = lifeArea;
        this.locationType = locationType;
        this.action = action;
        this.precondition = precondition;
        this.disclosureScope = disclosureScope;
        this.sourceExcerpt = sourceExcerpt;
        this.status = ItemStatus.PROPOSED;
    }

    public void approve() {
        this.status = ItemStatus.APPROVED;
        this.reviewedAt = LocalDateTime.now();
    }

    public void reject() {
        this.status = ItemStatus.REJECTED;
        this.reviewedAt = LocalDateTime.now();
    }

    public void assignRecipient(Recipient recipient) {
        this.recipient = recipient;
    }
}