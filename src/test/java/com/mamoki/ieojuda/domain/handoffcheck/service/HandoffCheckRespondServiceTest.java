package com.mamoki.ieojuda.domain.handoffcheck.service;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckRespondRequest;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckRespondResponse;
import com.mamoki.ieojuda.domain.handoffcheck.entity.HandoffCheck;
import com.mamoki.ieojuda.domain.handoffcheck.entity.HandoffCheckResponse;
import com.mamoki.ieojuda.domain.handoffcheck.repository.HandoffCheckResponseRepository;
import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.entity.RoleType;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.ratelimit.PublicLinkAuditor;
import com.mamoki.ieojuda.global.ratelimit.TokenLookupGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 명세서 "선택형 생전 인계 점검" - 담당자가 점검 링크로 응답하는 공개 엔드포인트의 성공/실패 경로를 검증한다.
class HandoffCheckRespondServiceTest {

    private HandoffCheckResponseRepository handoffCheckResponseRepository;
    private TokenLookupGuard tokenLookupGuard;
    private PublicLinkAuditor publicLinkAuditor;
    private HandoffCheckRespondService handoffCheckRespondService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        handoffCheckResponseRepository = mock(HandoffCheckResponseRepository.class);
        tokenLookupGuard = mock(TokenLookupGuard.class);
        publicLinkAuditor = mock(PublicLinkAuditor.class);
        handoffCheckRespondService = new HandoffCheckRespondService(
                handoffCheckResponseRepository, tokenLookupGuard, publicLinkAuditor);

        // TokenLookupGuard는 실제 구현처럼 supplier를 그대로 실행해 repository 목 설정이 그대로 동작하게 위임한다.
        when(tokenLookupGuard.resolve(anyString(), any())).thenAnswer(invocation -> {
            Supplier<Optional<?>> lookup = invocation.getArgument(1);
            return lookup.get().orElseThrow(() -> new CustomException(ErrorCode.TOKEN_INVALID));
        });
    }

    private HandoffCheckResponse buildPendingResponse(String plainToken, LocalDateTime expiresAt) {
        Plan plan = mock(Plan.class);
        User author = User.builder().email("owner@test.com").password("hash").name("김나무").build();
        when(plan.getUser()).thenReturn(author);

        HandoffCheck handoffCheck = mock(HandoffCheck.class);
        when(handoffCheck.getPlan()).thenReturn(plan);

        Recipient recipient = mock(Recipient.class);
        when(recipient.getRoleType()).thenReturn(RoleType.FAMILY_MANAGER);
        when(recipient.getDisclosureScope()).thenReturn(DisclosureScope.FAMILY);

        HandoffCheckResponse response = HandoffCheckResponse.builder().handoffCheck(handoffCheck).recipient(recipient).build();
        response.issueInviteToken(TokenProvider.hashToken(plainToken), expiresAt);
        return response;
    }

    @Test
    void respond_succeedsAndReturnsOnlyAllowedFields() {
        String plainToken = "valid-token";
        HandoffCheckResponse response = buildPendingResponse(plainToken, LocalDateTime.now().plusHours(1));
        when(handoffCheckResponseRepository.findByInviteToken(TokenProvider.hashToken(plainToken)))
                .thenReturn(Optional.of(response));

        HandoffCheckRespondRequest request = new HandoffCheckRespondRequest(true, true, false, "제 역할이 언제 시작되나요?");
        HandoffCheckRespondResponse result = handoffCheckRespondService.respond(plainToken, request);

        assertThat(result.roleType()).isEqualTo("FAMILY_MANAGER");
        assertThat(result.disclosureScope()).isEqualTo("FAMILY");
        assertThat(result.authorName()).isEqualTo("김나무");
        assertThat(result.respondedAt()).isNotNull();

        // 응답 저장 및 재사용 방지를 위한 토큰 무효화가 실제로 반영됐는지 확인
        assertThat(response.getEmailReached()).isTrue();
        assertThat(response.getRoleUnderstood()).isTrue();
        assertThat(response.getDisclosureUnderstood()).isFalse();
        assertThat(response.getInquiry()).isEqualTo("제 역할이 언제 시작되나요?");
        assertThat(response.getRespondedAt()).isNotNull();
        assertThat(response.getInviteToken()).isNull();
        assertThat(response.getInviteTokenExpiresAt()).isNull();
    }

    @Test
    void respond_throwsTokenInvalid_whenTokenUnknown() {
        when(handoffCheckResponseRepository.findByInviteToken(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handoffCheckRespondService.respond("unknown-token", validRequest()))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID));
    }

    @Test
    void respond_throwsAccessLinkExpired_whenTokenExpired() {
        String plainToken = "expired-token";
        HandoffCheckResponse response = buildPendingResponse(plainToken, LocalDateTime.now().minusMinutes(1));
        when(handoffCheckResponseRepository.findByInviteToken(TokenProvider.hashToken(plainToken)))
                .thenReturn(Optional.of(response));

        assertThatThrownBy(() -> handoffCheckRespondService.respond(plainToken, validRequest()))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_LINK_EXPIRED));

        verify(publicLinkAuditor).recordStateFailure(ErrorCode.ACCESS_LINK_EXPIRED);
        // 만료된 링크는 곧바로 무효화되어 재시도로도 살아나지 않아야 한다
        assertThat(response.getInviteToken()).isNull();
        assertThat(response.getRespondedAt()).isNull();
    }

    // 동시에 같은(아직 무효화되지 않은) 토큰으로 두 번째 요청이 들어오는 경합 상황을 흉내낸다
    @Test
    void respond_throwsAccessLinkAlreadyUsed_whenAlreadyResponded() {
        String plainToken = "raced-token";
        HandoffCheckResponse response = buildPendingResponse(plainToken, LocalDateTime.now().plusHours(1));
        response.respond(true, true, true, null); // 먼저 도착한 요청이 이미 응답을 채움 (respond()가 토큰도 무효화함)
        response.issueInviteToken(TokenProvider.hashToken(plainToken), LocalDateTime.now().plusHours(1)); // 경합 시나리오 재현을 위해 유효 토큰 스냅샷을 복원
        when(handoffCheckResponseRepository.findByInviteToken(TokenProvider.hashToken(plainToken)))
                .thenReturn(Optional.of(response));

        assertThatThrownBy(() -> handoffCheckRespondService.respond(plainToken, validRequest()))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_LINK_ALREADY_USED));

        verify(publicLinkAuditor).recordStateFailure(ErrorCode.ACCESS_LINK_ALREADY_USED);
    }

    private HandoffCheckRespondRequest validRequest() {
        return new HandoffCheckRespondRequest(true, true, true, null);
    }
}
