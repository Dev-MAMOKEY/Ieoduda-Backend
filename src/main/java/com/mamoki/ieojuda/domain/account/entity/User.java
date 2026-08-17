package com.mamoki.ieojuda.domain.account.entity;

import com.mamoki.ieojuda.global.entity.BaseCreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "email", length = 255, nullable = false, unique = true)
    private String email;

    @Column(name = "password", length = 255, nullable = false)
    private String password;

    @Column(name = "name", length = 100)
    private String name;

    // "사후 인계 안내" 화면 - 필수 동의 완료 시각(null이면 아직 미동의). 항목이 전부 한 버튼("확인하기")으로
    // 묶여있고 동의 종류가 하나뿐이라 별도 테이블 없이 컬럼 하나로 관리한다.
    @Column(name = "consent_agreed_at")
    private LocalDateTime consentAgreedAt;

    // 공개 회원가입(POST /auth/signup)으로는 항상 USER로만 생성됨 - ADMIN/EXTERNAL은 DB에서 role만 승격해서 부여한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    private UserRole role;

    // issue #59 - ADMIN/EXTERNAL 안에서도 업무 단위로 세분화된 권한. USER는 항상 비어있다.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_permissions", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", length = 30)
    private Set<AdminPermission> permissions = new HashSet<>();

    // issue #56 - 정지된 계정은 Access Token이 아직 안 만료됐어도 필터에서 즉시 거부한다
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private UserStatus status;

    // issue #56 - Access Token에 발급 당시 버전을 함께 실어두고, 필터에서 이 값과 대조한다.
    // 이메일 변경/정지 등 권한에 영향을 주는 이벤트가 생기면 이 값을 올려서, 만료 전까지 유효한
    // 기존 Access Token 전부를 한 번에 무효화한다(개별 토큰 블랙리스트 없이도 즉시 폐기 가능).
    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion;

    @Builder
    public User(String email, String password, String name) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.role = UserRole.USER;
        this.status = UserStatus.ACTIVE;
        this.tokenVersion = 0;
    }

    public boolean hasPermission(AdminPermission permission) {
        return permissions.contains(permission);
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

    public void incrementTokenVersion() {
        this.tokenVersion = this.tokenVersion + 1;
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    public void reactivate() {
        this.status = UserStatus.ACTIVE;
    }
}
