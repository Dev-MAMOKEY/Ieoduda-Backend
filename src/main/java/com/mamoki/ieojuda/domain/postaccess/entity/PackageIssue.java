package com.mamoki.ieojuda.domain.postaccess.entity;

import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "package_issues")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PackageIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "issue_id")
    private Long issueId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigne_id", nullable = false) // role_assigness.assigne_id 오탈자 그대로
    private Recipient recipient;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "reported_at")
    private LocalDateTime reportedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    private PackageIssueStatus status;

    @Builder
    public PackageIssue(Item item, Recipient recipient, String description) {
        this.item = item;
        this.recipient = recipient;
        this.description = description;
        this.reportedAt = LocalDateTime.now();
        this.status = PackageIssueStatus.OPEN;
    }

    //명세서 "역할별 사후 패키지" - 문제 신고
    public void resolve() {
        this.status = PackageIssueStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
    }
}
