package com.mamoki.ieojuda.partner.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "partner_reviewers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartnerReviewer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reviewer_id")
    private Long reviewerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    private ExternalPartner partner;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "is_active")
    private Boolean isActive;

    @Builder
    public PartnerReviewer(ExternalPartner partner, String name, String email) {
        this.partner = partner;
        this.name = name;
        this.email = email;
        this.isActive = true;
    }

    /** 이해충돌·권한 만료 시 비활성화 (명세서 "다른 검토자에게 재배정") */
    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }
}
