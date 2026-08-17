package com.mamoki.ieojuda.domain.partner.entity;

import com.mamoki.ieojuda.domain.account.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "partner_reviewers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartnerReviewer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "reviewer_id")
    private UUID reviewerId;

    // 이 검토자로 로그인하는 계정(role=EXTERNAL). DB에서 role을 승격시킬 때 같이 연결해줘야 한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "is_active")
    private Boolean isActive;

    @Builder
    public PartnerReviewer(User user, String name, String email) {
        this.user = user;
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
