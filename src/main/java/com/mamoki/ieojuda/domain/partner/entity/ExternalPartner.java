package com.mamoki.ieojuda.domain.partner.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "external_partners")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalPartner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "partner_id")
    private Long partnerId;

    @Column(name = "name", length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "partner_type", length = 30)
    private PartnerType partnerType;

    @Column(name = "contact_info", length = 255)
    private String contactInfo;

    @Builder
    public ExternalPartner(String name, PartnerType partnerType, String contactInfo) {
        this.name = name;
        this.partnerType = partnerType;
        this.contactInfo = contactInfo;
    }
}
