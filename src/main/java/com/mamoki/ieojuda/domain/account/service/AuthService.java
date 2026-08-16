package com.mamoki.ieojuda.domain.account.service;

import com.mamoki.ieojuda.domain.account.dto.LoginRequest;
import com.mamoki.ieojuda.domain.account.dto.RefreshRequest;
import com.mamoki.ieojuda.domain.account.dto.SignupRequest;
import com.mamoki.ieojuda.domain.account.dto.SignupResponse;
import com.mamoki.ieojuda.domain.account.dto.TokenResponse;
import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.audit.entity.AuthAuditEventType;
import com.mamoki.ieojuda.domain.audit.service.AuthAuditService;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.jwt.component.JwtTokenProvider;
import com.mamoki.ieojuda.global.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 명세서 "회원가입 / 로그인" 화면 - 계정 생성, 로그인(AT/RT 발급), RT로 AT 재발급
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordBreachChecker passwordBreachChecker;
    private final LoginAttemptService loginAttemptService;
    private final AuthAuditService authAuditService;
    private final HttpServletRequest httpServletRequest;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new CustomException(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }
        // "유출 비밀번호 검사"(issue #55) - 평문은 절대 밖으로 나가지 않고 해시 앞 5자리로만 조회한다
        if (passwordBreachChecker.isBreached(request.password())) {
            throw new CustomException(ErrorCode.BREACHED_PASSWORD);
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
        String normalizedEmail = request.email().trim().toLowerCase();
        String ip = ClientIpResolver.resolve(httpServletRequest);

        if (loginAttemptService.isLocked(normalizedEmail)) {
            authAuditService.record(normalizedEmail, ip, AuthAuditEventType.LOGIN_LOCKED_ATTEMPT, null);
            throw new CustomException(ErrorCode.ACCOUNT_TEMPORARILY_LOCKED);
        }

        // 계정 존재 여부가 드러나지 않도록 이메일 불일치/비밀번호 불일치 둘 다 같은 에러코드로 응답
        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            boolean nowLocked = loginAttemptService.recordFailure(normalizedEmail);
            authAuditService.record(normalizedEmail, ip,
                    nowLocked ? AuthAuditEventType.LOGIN_LOCKED : AuthAuditEventType.LOGIN_FAILURE, null);
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        loginAttemptService.recordSuccess(normalizedEmail);
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String refreshToken = request.refreshToken();

        // 서명/만료 검증은 JwtTokenProvider가 파싱하면서 자동으로 함 - 실패 시 ExpiredJwtException/JwtException이
        // 그대로 던져지고 GlobalExceptionHandler가 TOKEN_EXPIRED/TOKEN_INVALID로 응답한다.
        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new CustomException(ErrorCode.TOKEN_INVALID);
        }
        Long userId = jwtTokenProvider.getUserId(refreshToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        // DB에 저장된 RT와 요청으로 들어온 RT가 달라졌다면(로그아웃/재로그인 등으로 이미 교체됨) 재사용 불가
        if (user.getRefreshToken() == null || !user.getRefreshToken().equals(refreshToken)) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_REVOKED);
        }

        return issueTokens(user);
    }

    // /auth/**는 SecurityConfig에서 permitAll이라 토큰 없이도 요청 자체는 통과되므로, userId가 비어 있으면
    // (=Authorization 헤더 없이 호출한 경우) 여기서 직접 막는다.
    // AT는 만료 전까지 계속 유효하지만(블랙리스트 없음), 저장된 RT를 지워서 재발급은 더 이상 못 하게 막는다.
    @Transactional
    public void logout(Long userId) {
        if (userId == null) {
            throw new CustomException(ErrorCode.TOKEN_INVALID);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        user.updateRefreshToken(null);
    }

    // AT/RT 새로 발급하고 RT는 DB에 갱신 저장 (재발급마다 RT도 회전)
    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getUserId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId());
        user.updateRefreshToken(refreshToken);
        return new TokenResponse(accessToken, refreshToken);
    }
}
