package com.mamoki.ieojuda.domain.plan.service;

import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckAssigneeResponse;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckConfirmerResponse;
import com.mamoki.ieojuda.domain.handoffcheck.dto.HandoffCheckStatusResponse;
import com.mamoki.ieojuda.domain.handoffcheck.service.HandoffCheckService;
import com.mamoki.ieojuda.domain.plan.dto.LifeAreaSummaryResponse;
import com.mamoki.ieojuda.domain.plan.dto.OrderCheckItemResponse;
import com.mamoki.ieojuda.domain.plan.dto.PlanSummaryResponse;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.domain.releasecase.dto.ReleaseStatusResponse;
import com.mamoki.ieojuda.domain.releasecase.service.ReleaseStatusService;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 명세서 "계획 홈" 화면 - 프론트가 API 한 번으로 화면을 그릴 수 있도록 기존 서비스 4개의 조회 결과를 조합한다.
// 새 쿼리를 추가하지 않고 LifeAreaService/ItemOrderService/HandoffCheckService/ReleaseStatusService를 그대로 재사용한다.
//
// 부분 실패 처리 방침(#84 완료 조건): 네 조회 중 하나라도 예외가 나면 부분 데이터를 만들지 않고
// 요청 전체를 실패시킨다 - GlobalExceptionHandler가 표준 RsData 실패 응답으로 바꿔주고,
// 이 엔드포인트는 GET(멱등)이라 프론트는 그냥 재호출하면 된다. 이 저장소의 다른 조회 API도
// 전부 같은 방식(부분 응답 없음)이라 여기서만 다르게 갈 이유가 없다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanSummaryService {

    private final PlanRepository planRepository;
    private final LifeAreaService lifeAreaService;
    private final ItemOrderService itemOrderService;
    private final HandoffCheckService handoffCheckService;
    private final ReleaseStatusService releaseStatusService;

    public PlanSummaryResponse getMySummary(Long userId) {
        Plan plan = planRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAN_NOT_FOUND));
        Long planId = plan.getPlanId();

        var lifeAreas = lifeAreaService.getLifeAreas(userId, planId).stream()
                .map(LifeAreaSummaryResponse::from)
                .toList();

        HandoffCheckStatusResponse handoffCheck = handoffCheckService.getHandoffCheck(userId, planId);
        long recipientAcceptedCount = handoffCheck.assignees().stream()
                .filter(HandoffCheckAssigneeResponse::isRoleAccepted).count();
        long confirmerAcceptedCount = handoffCheck.confirmers().stream()
                .filter(HandoffCheckConfirmerResponse::isRoleAccepted).count();
        long backupMissingCount = handoffCheck.assignees().stream()
                .filter(assignee -> assignee.backupName() == null).count();
        List<String> confirmerNames = handoffCheck.confirmers().stream()
                .map(HandoffCheckConfirmerResponse::name).toList();

        // 충돌 1건마다 관련된 두 항목 모두 conflict=true로 표시되므로, 이 값은 "충돌 쌍의 수"가 아니라 "충돌에 걸린 항목 수"다.
        long unresolvedConflictCount = itemOrderService.getOrderCheck(userId, planId).items().stream()
                .filter(OrderCheckItemResponse::conflict).count();

        ReleaseStatusResponse releaseCase = releaseStatusService.getStatus(userId, planId);

        return new PlanSummaryResponse(
                plan.getPlanId(),
                plan.getStatus().name(),
                plan.getUser().getName(),
                lifeAreas,
                recipientAcceptedCount,
                handoffCheck.assigneeTotalCount(),
                confirmerAcceptedCount,
                handoffCheck.confirmerTotalCount(),
                confirmerNames,
                backupMissingCount,
                unresolvedConflictCount,
                plan.getWaitingDays(),
                releaseCase
        );
    }
}
