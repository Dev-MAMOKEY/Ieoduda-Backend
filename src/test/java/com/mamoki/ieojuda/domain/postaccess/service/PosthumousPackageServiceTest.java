package com.mamoki.ieojuda.domain.postaccess.service;

import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.plan.dto.PlanSnapshotDto;
import com.mamoki.ieojuda.domain.plan.entity.DisclosureScope;
import com.mamoki.ieojuda.domain.plan.entity.ItemActionType;
import com.mamoki.ieojuda.domain.plan.entity.ItemStatus;
import com.mamoki.ieojuda.domain.plan.entity.LifeArea;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.entity.PlanVersion;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.plan.service.PlanSnapshotService;
import com.mamoki.ieojuda.domain.postaccess.dto.PackageIssueRequest;
import com.mamoki.ieojuda.domain.postaccess.entity.AccessToken;
import com.mamoki.ieojuda.domain.postaccess.entity.PackageActionCompletion;
import com.mamoki.ieojuda.domain.postaccess.entity.PackageIssue;
import com.mamoki.ieojuda.domain.postaccess.repository.AccessTokenRepository;
import com.mamoki.ieojuda.domain.postaccess.repository.PackageActionCompletionRepository;
import com.mamoki.ieojuda.domain.postaccess.repository.PackageIssueRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.entity.RoleType;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import com.mamoki.ieojuda.domain.stage.service.HandoverStageService;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.idempotency.service.IdempotencyGuard;
import com.mamoki.ieojuda.global.ratelimit.PublicLinkAuditor;
import com.mamoki.ieojuda.global.ratelimit.TokenLookupGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// issue #77 - 역할별 사후 패키지 조회·완료·문제신고. 다른 역할 항목 접근 차단을 중점 검증한다.
class PosthumousPackageServiceTest {

    private static final String SESSION_ID = "session-token";
    private static final Long MY_RECIPIENT_ID = 4L;
    private static final Long OTHER_RECIPIENT_ID = 5L;

    private AccessTokenRepository accessTokenRepository;
    private PackageActionCompletionRepository packageActionCompletionRepository;
    private PackageIssueRepository packageIssueRepository;
    private ItemRepository itemRepository;
    private PlanSnapshotService planSnapshotService;
    private TokenLookupGuard tokenLookupGuard;
    private PublicLinkAuditor publicLinkAuditor;
    private IdempotencyGuard idempotencyGuard;
    private HandoverStageService handoverStageService;
    private PosthumousPackageService posthumousPackageService;

    private AccessToken accessToken;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        accessTokenRepository = mock(AccessTokenRepository.class);
        packageActionCompletionRepository = mock(PackageActionCompletionRepository.class);
        packageIssueRepository = mock(PackageIssueRepository.class);
        itemRepository = mock(ItemRepository.class);
        planSnapshotService = mock(PlanSnapshotService.class);
        tokenLookupGuard = mock(TokenLookupGuard.class);
        publicLinkAuditor = mock(PublicLinkAuditor.class);
        idempotencyGuard = mock(IdempotencyGuard.class);
        handoverStageService = mock(HandoverStageService.class);
        posthumousPackageService = new PosthumousPackageService(
                accessTokenRepository, packageActionCompletionRepository, packageIssueRepository,
                itemRepository, planSnapshotService, tokenLookupGuard, publicLinkAuditor, idempotencyGuard,
                handoverStageService);

        when(tokenLookupGuard.resolve(anyString(), any())).thenAnswer(invocation -> {
            Supplier<Optional<?>> lookup = invocation.getArgument(1);
            return lookup.get().orElseThrow(() -> new CustomException(ErrorCode.TOKEN_INVALID));
        });
        when(packageActionCompletionRepository.findByHandoverStage_StageId(any())).thenReturn(List.of());

        User user = mock(User.class);
        when(user.getName()).thenReturn("김나무");
        Plan plan = mock(Plan.class);
        when(plan.getUser()).thenReturn(user);
        LifeArea lifeArea = mock(LifeArea.class);

        Recipient myRecipient = Recipient.builder()
                .plan(plan).lifeArea(lifeArea).name("이지수").email("jisoo@test.com")
                .roleType(RoleType.RELATIONSHIP_MANAGER).isBackup(false)
                .disclosureScope(DisclosureScope.RELATIONSHIP).maxWaitHours(168).backupFor(null).build();
        setId(myRecipient, MY_RECIPIENT_ID);

        HandoverStage stage = HandoverStage.builder().plan(plan).recipient(myRecipient).stageOrder(0).build();
        setId(stage, 10L);
        stage.send();

        PlanVersion planVersion = mock(PlanVersion.class);
        when(planVersion.getSnapshotData()).thenReturn("{\"frozen\":true}");
        ReleaseCase releaseCase = ReleaseCase.builder().plan(plan).planVersion(planVersion).build();
        stage.assignToCase(releaseCase);

        accessToken = AccessToken.builder()
                .handoverStage(stage).tokenHash(TokenProvider.hashToken(SESSION_ID))
                .expiresAt(LocalDateTime.now().plusHours(1)).build();
        accessToken.verify(); // OTP 인증 통과한 상태로 시작 (verifiedAt now)
        when(accessTokenRepository.findByTokenHash(TokenProvider.hashToken(SESSION_ID)))
                .thenReturn(Optional.of(accessToken));

        // 스냅샷: 내 항목 1개(itemId=100) + 다른 담당자 항목 1개(itemId=200)
        PlanSnapshotDto.ItemSnapshot myItem = new PlanSnapshotDto.ItemSnapshot(
                100L, MY_RECIPIENT_ID, "이지수", "인스타그램", "비공개 전환", "SNS 계정 처리", "비공개로 전환",
                "", DisclosureScope.RELATIONSHIP, "지수에게 SNS 정리를 부탁", ItemStatus.APPROVED, 0, ItemActionType.DELETE);
        PlanSnapshotDto.ItemSnapshot otherItem = new PlanSnapshotDto.ItemSnapshot(
                200L, OTHER_RECIPIENT_ID, "다른사람", "이메일", "정리", "업무 메일 정리", "내용",
                "", DisclosureScope.WORK, "근거", ItemStatus.APPROVED, 0, ItemActionType.TRANSFER);
        PlanSnapshotDto snapshot = new PlanSnapshotDto(1L, 7, List.of(myItem, otherItem), List.of(), List.of());
        when(planSnapshotService.deserialize("{\"frozen\":true}")).thenReturn(snapshot);
    }

    private void setId(Object entity, Long id) {
        try {
            String fieldName = entity instanceof Recipient ? "assigneeId" : "stageId";
            var field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void getPackage_returnsOnlyMyItems_excludesOtherRolesAndSourceExcerpt() {
        var response = posthumousPackageService.getPackage(SESSION_ID);

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.roleLabel()).isEqualTo("관계 정리 담당자");
        assertThat(response.authorName()).isEqualTo("김나무");
        assertThat(response.notice()).contains("김나무");
        assertThat(response.actions()).hasSize(1);
        assertThat(response.actions().get(0).actionId()).isEqualTo(100L);
        assertThat(response.actions().get(0).action()).isEqualTo("비공개 전환");
        assertThat(response.actions().get(0).status()).isEqualTo("PENDING");
    }

    @Test
    void getPackage_whenSessionExpired_throwsAccessLinkExpired() {
        AccessToken expiredSession = AccessToken.builder()
                .handoverStage(accessToken.getHandoverStage())
                .tokenHash(TokenProvider.hashToken("expired-session"))
                .expiresAt(LocalDateTime.now().plusHours(1)).build();
        // verify()를 호출하지 않아 verifiedAt == null -> 세션 자체가 시작되지 않은 상태
        when(accessTokenRepository.findByTokenHash(TokenProvider.hashToken("expired-session")))
                .thenReturn(Optional.of(expiredSession));

        assertThatThrownBy(() -> posthumousPackageService.getPackage("expired-session"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_LINK_EXPIRED));
    }

    @Test
    void getPackage_reflectsCompletedCount() {
        PackageActionCompletion completion = PackageActionCompletion.builder()
                .handoverStage(accessToken.getHandoverStage()).itemId(100L).build();
        when(packageActionCompletionRepository.findByHandoverStage_StageId(10L)).thenReturn(List.of(completion));

        var response = posthumousPackageService.getPackage(SESSION_ID);

        assertThat(response.completedCount()).isEqualTo(1);
        assertThat(response.actions().get(0).status()).isEqualTo("COMPLETED");
    }

    // issue #77 완료 조건 - "다른 역할의 항목 요청 시 차단"
    @Test
    void completeAction_whenActionBelongsToOtherRole_throwsRolePackageAccessDenied() {
        assertThatThrownBy(() -> posthumousPackageService.completeAction(SESSION_ID, 200L, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ROLE_PACKAGE_ACCESS_DENIED));
        verify(packageActionCompletionRepository, never()).save(any());
    }

    @Test
    void completeAction_whenOwned_savesCompletionAndReturnsCompletedStatus() {
        when(packageActionCompletionRepository.findByHandoverStage_StageIdAndItemId(10L, 100L))
                .thenReturn(Optional.empty());

        var response = posthumousPackageService.completeAction(SESSION_ID, 100L, null);

        assertThat(response.status()).isEqualTo("COMPLETED");
        verify(packageActionCompletionRepository, times(1)).save(any());
        verify(idempotencyGuard).claim("package-action-complete", null);
        // issue #78 - 내 역할 항목이 1개뿐이라 이 완료로 단계 전체가 끝남 -> 단계 완료 판정 호출로 이어져야 한다
        verify(handoverStageService).completeStageIfAllActionsDone(10L, 1);
    }

    @Test
    void completeAction_whenAlreadyCompleted_doesNotDuplicateSave() {
        PackageActionCompletion existing = PackageActionCompletion.builder()
                .handoverStage(accessToken.getHandoverStage()).itemId(100L).build();
        when(packageActionCompletionRepository.findByHandoverStage_StageIdAndItemId(10L, 100L))
                .thenReturn(Optional.of(existing));

        posthumousPackageService.completeAction(SESSION_ID, 100L, "same-key");

        // 이미 완료된 행동을 재요청했을 때는 단계 완료 판정도 다시 트리거하지 않는다(불필요한 재판정 방지)
        verify(handoverStageService, never()).completeStageIfAllActionsDone(any(), org.mockito.ArgumentMatchers.anyInt());

        verify(packageActionCompletionRepository, never()).save(any());
    }

    @Test
    void reportIssue_whenActionBelongsToOtherRole_throwsRolePackageAccessDenied() {
        PackageIssueRequest request = new PackageIssueRequest(200L, "문제 있음");

        assertThatThrownBy(() -> posthumousPackageService.reportIssue(SESSION_ID, request))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ROLE_PACKAGE_ACCESS_DENIED));
        verify(packageIssueRepository, never()).save(any());
    }

    @Test
    void reportIssue_whenOwned_savesIssue() {
        when(itemRepository.getReferenceById(100L)).thenReturn(mock(com.mamoki.ieojuda.domain.plan.entity.Item.class));
        when(packageIssueRepository.save(any())).thenAnswer(invocation -> {
            PackageIssue issue = invocation.getArgument(0);
            var field = PackageIssue.class.getDeclaredField("issueId");
            field.setAccessible(true);
            field.set(issue, 1L);
            return issue;
        });

        var response = posthumousPackageService.reportIssue(SESSION_ID, new PackageIssueRequest(100L, "접근 불가"));

        assertThat(response.issueId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo("OPEN");
    }
}
