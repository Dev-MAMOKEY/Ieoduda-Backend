package com.mamoki.ieojuda.domain.account.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "consents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Consent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consent_id")
    private Long consentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", length = 30)
    private ConsentType consentType;

    @Column(name = "agreed_at")
    private LocalDateTime agreedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder
    public Consent(ConsentType consentType, User user) {
        this.consentType = consentType;
        this.user = user;
    }

    public void agree() {
        this.agreedAt = LocalDateTime.now();
    }
}