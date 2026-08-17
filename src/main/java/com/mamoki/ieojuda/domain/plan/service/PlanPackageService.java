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
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    public PackagePreviewResponse getPreview(UUID userId, UUID planId) {
        planOwnershipReader.findOwnedPlan(userId, planId);

        List<Item> items = itemRepository
                .findByLifeArea_Plan_PlanIdAndRecipientIsNotNullOrderBySortOrderAscItemIdAsc(planId);

        Map<UUID, List<Item>> itemsByRecipientId = new LinkedHashMap<>();
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

    // issue #90 (제안) - 한 담당자가 전체 항목의 과반(50% 초과)을 맡으면 권한 집중으로 판정.
    // ItemOrderService의 화면 표시용 판정과 같은 기준값을 쓴다.
    private static final double AUTHORITY_CONCENTRATION_THRESHOLD = 0.5;

    // 봉인 차단 검사(md 스펙 2-2절 5단계, 순서대로) - 권한 집중·실행순서확정 판정은 #81 당시
    // 기준이 없어 #90으로 미뤄졌고, 이번에 함께 반영한다.
    @Transactional
    public PlanResponse seal(UUID userId, UUID planId) {
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

        if (hasAuthorityConcentration(items)) {
            throw new CustomException(ErrorCode.PACKAGE_SEAL_BLOCKED);
        }

        if (plan.getOrderConfirmedAt() == null) {
            throw new CustomException(ErrorCode.ORDER_NOT_CONFIRMED);
        }

        plan.seal();
        return PlanResponse.from(plan);
    }

    private boolean hasAuthorityConcentration(List<Item> items) {
        Map<UUID, Long> countByRecipientId = items.stream()
                .collect(Collectors.groupingBy(item -> item.getRecipient().getAssigneeId(), Collectors.counting()));
        // 담당자가 한 명뿐이면 "몰림"을 비교할 대상 자체가 없다 - 재분배할 다른 담당자가 있을 때만 의미 있는 경고
        if (countByRecipientId.size() < 2) {
            return false;
        }
        return countByRecipientId.values().stream().anyMatch(count -> (double) count / items.size() > AUTHORITY_CONCENTRATION_THRESHOLD);
    }

    private boolean containsCredential(Item item) {
        return CredentialDetector.containsCredential(item.getAction())
                || CredentialDetector.containsCredential(item.getTitle())
                || CredentialDetector.containsCredential(item.getContent())
                || CredentialDetector.containsCredential(item.getPrecondition())
                || CredentialDetector.containsCredential(item.getTargetName())
                || CredentialDetector.containsCredential(item.getLocationType());
    }
}
