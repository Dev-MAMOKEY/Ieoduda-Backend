package com.mamoki.ieojuda.domain.account.entity;

import com.mamoki.ieojuda.global.entity.BaseCreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "email", length = 255, nullable = false, unique = true)
    private String email;

    @Column(name = "password", length = 255, nullable = false)
    private String password;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "refresh_token", length = 255)
    private String refreshToken;

    // "사후 인계 안내" 화면 - 필수 동의 완료 시각(null이면 아직 미동의). 항목이 전부 한 버튼("확인하기")으로
    // 묶여있고 동의 종류가 하나뿐이라 별도 테이블 없이 컬럼 하나로 관리한다.
    @Column(name = "consent_agreed_at")
    private LocalDateTime consentAgreedAt;

    // 공개 회원가입(POST /auth/signup)으로는 항상 USER로만 생성됨 - ADMIN/EXTERNAL은 DB에서 role만 승격해서 부여한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    private UserRole role;

    @Builder
    public User(String email, String password, String name) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.role = UserRole.USER;
    }

    public void updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    // 마이페이지 - 이메일/이름 변경
    public void updateProfile(String email, String name) {
        this.email = email;
        this.name = name;
    }

    // "사후 인계 안내" 필수 동의 - 이미 동의했으면 재호출해도 기존 시각을 유지(덮어쓰지 않음)
    public void agreeToConsent() {
        if (this.consentAgreedAt == null) {
            this.consentAgreedAt = LocalDateTime.now();
        }
    }
}
