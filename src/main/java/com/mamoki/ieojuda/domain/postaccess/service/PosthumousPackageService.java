package com.mamoki.ieojuda.domain.postaccess.service;

import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.postaccess.dto.PackageActionResponse;
import com.mamoki.ieojuda.domain.postaccess.dto.PackageIssueRequest;
import com.mamoki.ieojuda.domain.postaccess.dto.PackageIssueResponse;
import com.mamoki.ieojuda.domain.postaccess.dto.PosthumousPackageResponse;
import com.mamoki.ieojuda.domain.postaccess.entity.AccessToken;
import com.mamoki.ieojuda.domain.postaccess.entity.PackageActionStatus;
import com.mamoki.ieojuda.domain.postaccess.entity.PackageIssue;
import com.mamoki.ieojuda.domain.postaccess.repository.AccessTokenRepository;
import com.mamoki.ieojuda.domain.postaccess.repository.PackageIssueRepository;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCase;
import com.mamoki.ieojuda.domain.releasecase.entity.ReleaseCaseStatus;
import com.mamoki.ieojuda.domain.stage.entity.HandoverStage;
import com.mamoki.ieojuda.global.config.AppProperties;
import com.mamoki.ieojuda.global.email.token.TokenValidator;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

// 명세서 "역할별 사후 패키지" 화면 - OTP 확인을 통과한 담당자가 본인 항목만 확인하고 완료/문제 신고를 한다.
// 로그인이 없으므로 OTP 검증이 끝난 접근 세션(AccessToken)이 곧 인증 수단이다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PosthumousPackageService {

    private final AccessTokenRepository accessTokenRepository;
    private final ItemRepository itemRepository;
    private final PackageIssueRepository packageIssueRepository;
    private final AppProperties appProperties;

    public PosthumousPackageResponse getPackage(Long accessSessionId) {
        AccessToken session = findVerifiedSession(accessSessionId);
        return toPackageResponse(session);
    }

    // 화면의 "완료하기" - 이미 완료된 항목은 최초 완료 시각을 유지한다(Item.completeAction)
    @Transactional
    public PosthumousPackageResponse completeAction(Long accessSessionId, Long itemId) {
        AccessToken session = findVerifiedSession(accessSessionId);
        List<Item> items = findMyItems(session);

        findMyItem(items, itemId).completeAction();

        // 배정된 항목을 전부 끝냈으면 이 담당자의 단계도 완료로 남긴다.
        // 다음 단계 담당자에게 이어서 발송하는 처리는 아직 없다(명세서 7-3 "단계 완료 / 대체 담당자" 범위).
        if (isAllCompleted(items)) {
            session.getHandoverStage().complete();
        }

        return toPackageResponse(session, items);
    }

    // 화면의 "문제 신고하기" - 접수 기록만 남긴다.
    // 발송 정지·대체 담당자 전환은 운영자 전용 fallback API(명세서 7-3)가 담당한다.
    @Transactional
    public PackageIssueResponse reportIssue(Long accessSessionId, PackageIssueRequest request) {
        AccessToken session = findVerifiedSession(accessSessionId);
        Item item = findMyItem(findMyItems(session), request.itemId());

        PackageIssue issue = packageIssueRepository.save(PackageIssue.builder()
                .item(item)
                .recipient(session.getHandoverStage().getRecipient())
                .description(request.description())
                .build());

        return PackageIssueResponse.from(issue);
    }

    // OTP 확인을 통과한 세션인지 검사. 세션 존재 자체를 노출하지 않기 위해 미검증·없는 ID를 같은 예외로 묶는다
    private AccessToken findVerifiedSession(Long accessSessionId) {
        AccessToken session = accessTokenRepository.findById(accessSessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROLE_PACKAGE_ACCESS_DENIED));

        if (session.getVerifiedAt() == null) {
            throw new CustomException(ErrorCode.ROLE_PACKAGE_ACCESS_DENIED);
        }

        Instant sessionExpiresAt = session.getVerifiedAt()
                .plusMinutes(appProperties.getPosthumousSessionTtlMinutes())
                .atZone(ZoneId.systemDefault()).toInstant();
        if (TokenValidator.isExpired(sessionExpiresAt, Instant.now())) {
            throw new CustomException(ErrorCode.ACCESS_LINK_EXPIRED);
        }

        ReleaseCase releaseCase = session.getHandoverStage().getReleaseCase();
        if (Boolean.TRUE.equals(releaseCase.getFrozen())) {
            throw new CustomException(ErrorCode.RELEASE_CASE_FROZEN);
        }
        if (releaseCase.getStatus() != ReleaseCaseStatus.RELEASING) {
            throw new CustomException(ErrorCode.DISPUTE_RAISED);
        }

        return session;
    }

    private List<Item> findMyItems(AccessToken session) {
        return itemRepository.findByRecipient_AssigneeIdOrderBySortOrderAscItemIdAsc(
                session.getHandoverStage().getRecipient().getAssigneeId());
    }

    // 다른 역할의 항목 ID를 넣어 열람·조작하는 요청을 차단한다
    private Item findMyItem(List<Item> myItems, Long itemId) {
        return myItems.stream()
                .filter(item -> item.getItemId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.ROLE_PACKAGE_ACCESS_DENIED));
    }

    private boolean isAllCompleted(List<Item> items) {
        return items.stream().allMatch(item -> item.getCompletedAt() != null);
    }

    private PosthumousPackageResponse toPackageResponse(AccessToken session) {
        return toPackageResponse(session, findMyItems(session));
    }

    // 미완료 항목 중 실행 순서가 가장 앞선 하나만 "진행 중", 그 뒤는 앞 단계가 끝나야 열리므로 "대기"
    private PosthumousPackageResponse toPackageResponse(AccessToken session, List<Item> items) {
        List<PackageActionResponse> actions = new ArrayList<>();
        int completedCount = 0;
        boolean isInProgressAssigned = false;

        for (Item item : items) {
            PackageActionStatus status;
            if (item.getCompletedAt() != null) {
                status = PackageActionStatus.COMPLETED;
                completedCount++;
            } else if (!isInProgressAssigned) {
                status = PackageActionStatus.IN_PROGRESS;
                isInProgressAssigned = true;
            } else {
                status = PackageActionStatus.PENDING;
            }
            actions.add(PackageActionResponse.of(item, status));
        }

        HandoverStage stage = session.getHandoverStage();
        return PosthumousPackageResponse.of(stage, actions, completedCount);
    }
}
