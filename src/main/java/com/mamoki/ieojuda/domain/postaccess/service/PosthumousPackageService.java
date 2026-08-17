package com.mamoki.ieojuda.domain.postaccess.service;

import com.mamoki.ieojuda.domain.plan.dto.PlanSnapshotDto;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.plan.service.PlanSnapshotService;
import com.mamoki.ieojuda.domain.postaccess.dto.PackageActionResponse;
import com.mamoki.ieojuda.domain.postaccess.dto.PackageIssueRequest;
import com.mamoki.ieojuda.domain.postaccess.dto.PackageIssueResponse;
import com.mamoki.ieojuda.domain.postaccess.dto.PosthumousPackageResponse;
import com.mamoki.ieojuda.domain.postaccess.entity.AccessToken;
import com.mamoki.ieojuda.domain.postaccess.entity.PackageActionCompletion;
import com.mamoki.ieojuda.domain.postaccess.entity.PackageIssue;
import com.mamoki.ieojuda.domain.postaccess.repository.AccessTokenRepository;
import com.mamoki.ieojuda.domain.postaccess.repository.PackageActionCompletionRepository;
import com.mamoki.ieojuda.domain.postaccess.repository.PackageIssueRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.global.email.token.TokenProvider;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.idempotency.service.IdempotencyGuard;
import com.mamoki.ieojuda.global.ratelimit.PublicLinkAuditor;
import com.mamoki.ieojuda.global.ratelimit.TokenLookupGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

// 명세서 "역할별 사후 패키지" - OTP 인증을 통과한 담당자가 자신의 역할에 필요한 대상·행동·순서만
// 확인한다. 데이터 출처는 반드시 봉인된 스냅샷이며 라이브 Item 테이블을 조회하지 않는다(issue #42).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PosthumousPackageService {

    private static final String COMPLETE_IDEMPOTENCY_SCOPE = "package-action-complete";

    private final AccessTokenRepository accessTokenRepository;
    private final PackageActionCompletionRepository packageActionCompletionRepository;
    private final PackageIssueRepository packageIssueRepository;
    private final ItemRepository itemRepository;
    private final PlanSnapshotService planSnapshotService;
    private final TokenLookupGuard tokenLookupGuard;
    private final PublicLinkAuditor publicLinkAuditor;
    private final IdempotencyGuard idempotencyGuard;

    // ① 패키지 조회 - 자기 역할 항목만, 다른 역할·자격증명 원문은 응답에 포함하지 않는다
    @Transactional
    public PosthumousPackageResponse getPackage(String accessSessionId) {
        AccessToken accessToken = findValidSession(accessSessionId);
        Recipient recipient = accessToken.getHandoverStage().getRecipient();
        PlanSnapshotDto snapshot = deserializeSnapshot(accessToken);

        List<PlanSnapshotDto.ItemSnapshot> myItems = itemsOf(snapshot, recipient.getAssigneeId());
        Set<Long> completedItemIds = completedItemIds(accessToken.getHandoverStage().getStageId());

        List<PackageActionResponse> actions = myItems.stream()
                .map(item -> PackageActionResponse.of(item, completedItemIds.contains(item.itemId())))
                .toList();

        String authorName = recipient.getPlan().getUser().getName();
        return new PosthumousPackageResponse(
                recipient.getRoleType().label(),
                authorName,
                myItems.size(),
                (int) myItems.stream().filter(item -> completedItemIds.contains(item.itemId())).count(),
                "이 인계는 %s님이 생전에 이어두다를 통해 공식적으로 설정한 거예요.".formatted(authorName),
                actions
        );
    }

    // ② 행동 완료
    @Transactional
    public PackageActionResponse completeAction(String accessSessionId, Long actionId, String idempotencyKey) {
        idempotencyGuard.claim(COMPLETE_IDEMPOTENCY_SCOPE, idempotencyKey);

        AccessToken accessToken = findValidSession(accessSessionId);
        Recipient recipient = accessToken.getHandoverStage().getRecipient();
        PlanSnapshotDto.ItemSnapshot item = findOwnedItem(accessToken, recipient, actionId);

        Long stageId = accessToken.getHandoverStage().getStageId();
        boolean alreadyCompleted = packageActionCompletionRepository
                .findByHandoverStage_StageIdAndItemId(stageId, actionId).isPresent();
        if (!alreadyCompleted) {
            packageActionCompletionRepository.save(PackageActionCompletion.builder()
                    .handoverStage(accessToken.getHandoverStage())
                    .itemId(actionId)
                    .build());
        }

        return PackageActionResponse.of(item, true);
    }

    // ③ 문제 신고
    @Transactional
    public PackageIssueResponse reportIssue(String accessSessionId, PackageIssueRequest request) {
        AccessToken accessToken = findValidSession(accessSessionId);
        Recipient recipient = accessToken.getHandoverStage().getRecipient();
        findOwnedItem(accessToken, recipient, request.actionId()); // 소유·존재 검증

        PackageIssue issue = packageIssueRepository.save(PackageIssue.builder()
                .item(itemRepository.getReferenceById(request.actionId())) // FK 연결용 참조일 뿐 내용을 읽지 않는다
                .recipient(recipient)
                .description(request.reason())
                .build());

        return PackageIssueResponse.from(issue);
    }

    private AccessToken findValidSession(String accessSessionId) {
        AccessToken accessToken = tokenLookupGuard.resolve(accessSessionId,
                () -> accessTokenRepository.findByTokenHash(TokenProvider.hashToken(accessSessionId)));
        if (!accessToken.isSessionValid(LocalDateTime.now())) {
            publicLinkAuditor.recordStateFailure(ErrorCode.ACCESS_LINK_EXPIRED);
            throw new CustomException(ErrorCode.ACCESS_LINK_EXPIRED);
        }
        return accessToken;
    }

    // 요청한 actionId가 실제로 이 세션 담당자의 항목인지 매번 검사 - 다른 역할 요청 차단
    private PlanSnapshotDto.ItemSnapshot findOwnedItem(AccessToken accessToken, Recipient recipient, Long actionId) {
        PlanSnapshotDto snapshot = deserializeSnapshot(accessToken);
        return itemsOf(snapshot, recipient.getAssigneeId()).stream()
                .filter(item -> item.itemId().equals(actionId))
                .findFirst()
                .orElseThrow(() -> {
                    publicLinkAuditor.recordStateFailure(ErrorCode.ROLE_PACKAGE_ACCESS_DENIED);
                    return new CustomException(ErrorCode.ROLE_PACKAGE_ACCESS_DENIED);
                });
    }

    private PlanSnapshotDto deserializeSnapshot(AccessToken accessToken) {
        String snapshotData = accessToken.getHandoverStage().getReleaseCase().getPlanVersion().getSnapshotData();
        return planSnapshotService.deserialize(snapshotData);
    }

    private List<PlanSnapshotDto.ItemSnapshot> itemsOf(PlanSnapshotDto snapshot, Long recipientId) {
        return snapshot.items().stream()
                .filter(item -> recipientId.equals(item.recipientId()))
                .toList();
    }

    private Set<Long> completedItemIds(Long stageId) {
        return packageActionCompletionRepository.findByHandoverStage_StageId(stageId).stream()
                .map(PackageActionCompletion::getItemId)
                .collect(java.util.stream.Collectors.toSet());
    }
}
