package com.mamoki.ieojuda.domain.account.service;

import com.mamoki.ieojuda.domain.account.dto.LoginRequest;
import com.mamoki.ieojuda.domain.account.dto.RefreshRequest;
import com.mamoki.ieojuda.domain.account.dto.SignupRequest;
import com.mamoki.ieojuda.domain.account.dto.SignupResponse;
import com.mamoki.ieojuda.domain.account.dto.TokenResponse;
import com.mamoki.ieojuda.domain.account.entity.RefreshSession;
import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.RefreshSessionRepository;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.jwt.component.JwtTokenProvider;
import com.mamoki.ieojuda.global.jwt.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

// 명세서 "회원가입 / 로그인" 화면 - 계정 생성, 로그인(AT/RT 발급), RT로 AT 재발급
//
// issue #56 - Refresh Token은 세션(RefreshSession) 단위로 관리한다. 로그인 1회가 하나의 family를 열고,
// 재발급(회전)마다 같은 family 안에서 세션이 이어진다. 회전으로 이미 소모된 세션이 다시 제시되면
// 탈취로 간주해 family 전체를 차단한다(재사용 탐지).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RefreshSessionRepository refreshSessionRepository;
    private final SessionRevocationService sessionRevocationService;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new CustomException(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        User user = userRepository.save(User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .build());

        // 1인 1계획 고정 - "계획 만들기" 화면이 없어졌으므로 회원가입과 동시에 사후 인계 케이스(Plan)를 자동 생성한다.
        planRepository.save(Plan.builder().user(user).build());

        return SignupResponse.from(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        // 계정 존재 여부가 드러나지 않도록 이메일 불일치/비밀번호 불일치 둘 다 같은 에러코드로 응답
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 새 로그인 = 새 세션 family의 시작
        return issueSession(user, UUID.randomUUID().toString(), null);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String refreshTokenJwt = request.refreshToken();

        // 서명/발급자/대상/만료 검증은 JwtTokenProvider가 파싱하면서 자동으로 함 - 실패 시 ExpiredJwtException/JwtException이
        // 그대로 던져지고 GlobalExceptionHandler가 TOKEN_EXPIRED/TOKEN_INVALID로 응답한다.
        if (!jwtTokenProvider.isRefreshToken(refreshTokenJwt)) {
            throw new CustomException(ErrorCode.TOKEN_INVALID);
        }

        String sessionId = jwtTokenProvider.getJti(refreshTokenJwt);
        RefreshSession session = refreshSessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.TOKEN_INVALID));

        if (!session.getTokenHash().equals(TokenProvider.hashToken(refreshTokenJwt))) {
            throw new CustomException(ErrorCode.TOKEN_INVALID);
        }
        if (session.getRevokedAt() != null) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_REVOKED);
        }
        if (session.getUsedAt() != null) {
            // 이미 회전(소모)된 세션이 다시 제시됨 - 토큰 탈취로 간주하고 같은 family 전체를 차단.
            // 아래에서 곧바로 예외를 던져 이 트랜잭션은 롤백되므로, 차단 자체는 독립 트랜잭션으로 커밋해야 살아남는다.
            sessionRevocationService.revokeFamily(session.getFamilyId());
            throw new CustomException(ErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
        }

        User user = userRepository.findById(session.getUser().getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        return issueSession(user, session.getFamilyId(), session);
    }

    // /auth/**는 SecurityConfig에서 permitAll이라 토큰 없이도 요청 자체는 통과되므로, userId가 비어 있으면
    // (=Authorization 헤더 없이 호출한 경우) 여기서 직접 막는다.
    // AT는 만료 전까지 계속 유효하지만 tokenVersion 대조로 걸러진다 - 로그아웃 시 tokenVersion도 함께 올려서
    // 이미 발급된 Access Token도 즉시 폐기되게 한다.
    @Transactional
    public void logout(Long userId) {
        if (userId == null) {
            throw new CustomException(ErrorCode.TOKEN_INVALID);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        sessionRevocationService.revokeAllSessions(user);
    }

    // 새 세션을 발급한다. previousSession이 있으면(재발급) 그 세션을 "소모됨" 상태로 이어붙인다.
    private TokenResponse issueSession(User user, String familyId, RefreshSession previousSession) {
        String sessionId = UUID.randomUUID().toString();
        String refreshTokenJwt = jwtTokenProvider.generateRefreshToken(
                user.getUserId(), sessionId, jwtProperties.getRefreshTokenExpirationMs());
        String accessTokenJwt = jwtTokenProvider.generateAccessToken(
                user.getUserId(), user.getEmail(), user.getRole().name(), user.getTokenVersion());

        LocalDateTime now = LocalDateTime.now();
        refreshSessionRepository.save(RefreshSession.builder()
                .sessionId(sessionId)
                .user(user)
                .familyId(familyId)
                .tokenHash(TokenProvider.hashToken(refreshTokenJwt))
                .issuedAt(now)
                .expiresAt(now.plus(java.time.Duration.ofMillis(jwtProperties.getRefreshTokenExpirationMs())))
                .build());

        if (previousSession != null) {
            previousSession.markUsed(sessionId);
        }

        return new TokenResponse(accessTokenJwt, refreshTokenJwt);
    }
}
