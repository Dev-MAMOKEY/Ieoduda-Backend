package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.plan.dto.PackageActionPreview;
import com.mamoki.ieojuda.domain.plan.dto.PackagePreviewResponse;
import com.mamoki.ieojuda.domain.plan.dto.PlanResponse;
import com.mamoki.ieojuda.domain.plan.dto.RolePackagePreview;
import com.mamoki.ieojuda.domain.plan.entity.Item;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.recipient.entity.AcceptanceStatus;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.validation.CredentialDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 명세서 "역할별 패키지 미리보기" - 작성자가 생전에 역할별 공개 내용을 검토하고 직접 승인(봉인)한다.
// issue #81: 봉인 주체를 작성자로 옮기되, 사망 신고 시점의 PlanVersion 스냅샷 봉인(DeathReportService)은
// 별개로 그대로 둔다(부록 결정 (a) - 둘 다 유지).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanPackageService {

    private final PlanOwnershipReader planOwnershipReader;
    private final ItemRepository itemRepository;
    private final ConfirmerRepository confirmerRepository;

    public PackagePreviewResponse getPreview(Long userId, Long planId) {
        planOwnershipReader.findOwnedPlan(userId, planId);

        List<Item> items = itemRepository
                .findByLifeArea_Plan_PlanIdAndRecipientIsNotNullOrderBySortOrderAscItemIdAsc(planId);

        Map<Long, List<Item>> itemsByRecipientId = new LinkedHashMap<>();
        for (Item item : items) {
            itemsByRecipientId.computeIfAbsent(item.getRecipient().getAssigneeId(), key -> new ArrayList<>()).add(item);
        }

        List<RolePackagePreview> packages = itemsByRecipientId.values().stream()
                .map(group -> RolePackagePreview.of(
                        group.get(0).getRecipient(),
                        group.stream().map(PackageActionPreview::from).toList()))
                .toList();

        return new PackagePreviewResponse(packages);
    }

    // 봉인 차단 검사(이슈 #81 명시 범위 3가지, 순서대로) - 과도한 권한 집중 판정은 기준 비율이 아직
    // 정해지지 않아 #90에서 다룬다.
    @Transactional
    public PlanResponse seal(Long userId, Long planId) {
        Plan plan = planOwnershipReader.findOwnedPlan(userId, planId);

        long acceptedConfirmerCount = confirmerRepository.findByPlan_PlanIdOrderByConfirmIdAsc(planId).stream()
                .filter(confirmer -> confirmer.getAcceptanceStatus() == AcceptanceStatus.ACCEPTED)
                .count();
        if (acceptedConfirmerCount < 2) {
            throw new CustomException(ErrorCode.INSUFFICIENT_CONFIRMERS);
        }

        List<Item> items = itemRepository
                .findByLifeArea_Plan_PlanIdAndRecipientIsNotNullOrderBySortOrderAscItemIdAsc(planId);
        for (Item item : items) {
            if (containsCredential(item)) {
                throw new CustomException(ErrorCode.PACKAGE_SEAL_BLOCKED);
            }
            if (item.getSourceExcerpt() == null || item.getSourceExcerpt().isBlank()) {
                throw new CustomException(ErrorCode.PACKAGE_SEAL_BLOCKED);
            }
        }

        plan.seal();
        return PlanResponse.from(plan);
    }

    private boolean containsCredential(Item item) {
        return CredentialDetector.containsCredential(item.getTitle())
                || CredentialDetector.containsCredential(item.getContent())
                || CredentialDetector.containsCredential(item.getPrecondition())
                || CredentialDetector.containsCredential(item.getTargetName())
                || CredentialDetector.containsCredential(item.getLocationType());
    }
}
