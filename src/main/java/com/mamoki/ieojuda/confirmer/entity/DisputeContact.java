package com.mamoki.ieojuda.confirmer.entity;

import com.mamoki.ieojuda.plan.entity.Plan;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "dispute_contacts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DisputeContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contact_id")
    private Long contactId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "is_verified")
    private Boolean isVerified;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Builder
    public DisputeContact(Plan plan, String email, String name) {
        this.plan = plan;
        this.email = email;
        this.name = name;
        this.isVerified = false;
    }

    // 이메일 검증 완료
    public void verify() {
        this.isVerified = true;
        this.verifiedAt = LocalDateTime.now();
    }
}
