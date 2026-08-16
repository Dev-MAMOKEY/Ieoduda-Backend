package com.mamoki.ieojuda.domain.account.service;

import com.mamoki.ieojuda.domain.account.dto.ConsentResponse;
import com.mamoki.ieojuda.domain.account.dto.UserResponse;
import com.mamoki.ieojuda.domain.account.dto.UserUpdateRequest;
import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.confirmer.entity.Confirmer;
import com.mamoki.ieojuda.domain.confirmer.entity.DisputeContact;
import com.mamoki.ieojuda.domain.confirmer.repository.ConfirmerRepository;
import com.mamoki.ieojuda.domain.confirmer.repository.DisputeContactRepository;
import com.mamoki.ieojuda.domain.evidence.entity.Evidence;
import com.mamoki.ieojuda.domain.evidence.repository.EvidenceRepository;
import com.mamoki.ieojuda.domain.audit.repository.EmailLogRepository;
import com.mamoki.ieojuda.domain.handoffcheck.repository.HandoffCheckRepository;
import com.mamoki.ieojuda.domain.handoffcheck.repository.HandoffCheckResponseRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.ConversationRepository;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaMessageRepository;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanVersionRepository;
import com.mamoki.ieojuda.domain.postaccess.repository.PackageIssueRepository;
import com.mamoki.ieojuda.domain.recipient.entity.Recipient;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.domain.releasecase.repository.ObjectionRepository;
import com.mamoki.ieojuda.domain.releasecase.repository.ReleaseCaseRepository;
import com.mamoki.ieojuda.domain.releasecase.service.ReleaseCaseGuardService;
import com.mamoki.ieojuda.domain.stage.repository.DependencyRepository;
import com.mamoki.ieojuda.domain.stage.repository.HandoverStageRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
import com.mamoki.ieojuda.global.storage.EvidenceStorageClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 명세서 "마이페이지" 화면 - 계정 정보 변경 / 계정 삭제
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final ConversationRepository conversationRepository;
    private final LifeAreaRepository lifeAreaRepository;
    private final LifeAreaMessageRepository lifeAreaMessageRepository;
    private final ItemRepository itemRepository;
    private final RecipientRepository recipientRepository;
    private final DisputeContactRepository disputeContactRepository;
    private final ConfirmerRepository confirmerRepository;
    private final ReleaseCaseRepository releaseCaseRepository;
    private final PlanVersionRepository planVersionRepository;
    private final EvidenceRepository evidenceRepository;
    private final EmailLogRepository emailLogRepository;
    private final HandoverStageRepository handoverStageRepository;
    private final ObjectionRepository objectionRepository;
    private final HandoffCheckRepository handoffCheckRepository;
    private final HandoffCheckResponseRepository handoffCheckResponseRepository;
    private final PackageIssueRepository packageIssueRepository;
    private final DependencyRepository dependencyRepository;
    private final EvidenceStorageClient evidenceStorageClient;
    private final ReleaseCaseGuardService releaseCaseGuardService;

    @Transactional
    public UserResponse updateProfile(Long userId, UserUpdateRequest request) {
        User user = findUser(userId);
        boolean emailChanged = !user.getEmail().equals(request.email());

        if (emailChanged && userRepository.existsByEmailAndUserIdNot(request.email(), userId)) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        user.updateProfile(request.email(), request.name());

        // 로그인 이메일이 실제로 바뀌면(계정 탈취 대응) 이 계획에 걸린 담당자/확인자/이의연락처의
        // 기존 초대 토큰을 전부 무효화한다 - 새 이메일로 재초대해야 다시 접근할 수 있다.
        if (emailChanged) {
            planRepository.findByUser_UserId(userId).ifPresent(plan -> invalidatePlanInviteTokens(plan.getPlanId()));
        }

        return UserResponse.from(user);
    }

    private void invalidatePlanInviteTokens(Long planId) {
        recipientRepository.findByPlan_PlanId(planId).forEach(Recipient::invalidateInviteToken);
        confirmerRepository.findByPlan_PlanIdOrderByConfirmIdAsc(planId).forEach(Confirmer::invalidateInviteToken);
        disputeContactRepository.findByPlan_PlanId(planId).forEach(DisputeContact::invalidateInviteToken);
    }

    // "사후 인계 안내" 화면 - 필수 동의 조회/기록. ConsentCheckFilter가 이 값으로 전체 API 접근을 막는다.
    public ConsentResponse getConsentStatus(Long userId) {
        return ConsentResponse.from(findUser(userId));
    }

    @Transactional
    public ConsentResponse agree(Long userId) {
        User user = findUser(userId);
        user.agreeToConsent();
        return ConsentResponse.from(user);
    }

    // 계정·계획 데이터를 영구 삭제한다(되돌릴 수 없음).
    // 진행 중인 사후 인계 사건이 있으면 삭제하지 않고, 그 사건을 동결한 뒤 막는다(운영자 검토 대상).
    @Transactional
    public void deleteAccount(Long userId) {
        User user = findUser(userId);

        planRepository.findByUser_UserId(userId).ifPresent(plan -> {
            if (releaseCaseGuardService.freezeIfActiveCaseExists(plan.getPlanId())) {
                throw new CustomException(ErrorCode.ACTIVE_RELEASE_CASE_EXISTS);
            }
            deletePlanData(plan);
        });

        userRepository.delete(user);
    }

    // 자식 -> 부모 순서로 삭제 (FK 제약 위반 방지)
    private void deletePlanData(Plan plan) {
        Long planId = plan.getPlanId();

        emailLogRepository.deleteAll(emailLogRepository.findByPlan_PlanIdOrderBySentAtDesc(planId));
        deleteEvidenceWithStorage(planId);
        objectionRepository.deleteAll(objectionRepository.findByPlan_PlanId(planId));
        handoffCheckResponseRepository.deleteAll(handoffCheckResponseRepository.findByHandoffCheck_Plan_PlanId(planId));
        handoffCheckRepository.deleteAll(handoffCheckRepository.findByPlan_PlanId(planId));
        packageIssueRepository.deleteAll(packageIssueRepository.findByRecipient_Plan_PlanId(planId));
        dependencyRepository.deleteAll(dependencyRepository.findByPlan_PlanId(planId));
        lifeAreaMessageRepository.deleteAll(lifeAreaMessageRepository.findByConversation_Plan_PlanId(planId));
        itemRepository.deleteAll(itemRepository.findByLifeArea_Plan_PlanIdOrderByItemIdAsc(planId));
        handoverStageRepository.deleteAll(handoverStageRepository.findByPlan_PlanId(planId));
        recipientRepository.deleteAll(recipientRepository.findByPlan_PlanId(planId));
        disputeContactRepository.deleteAll(disputeContactRepository.findByPlan_PlanId(planId));
        releaseCaseRepository.deleteAll(releaseCaseRepository.findByPlan_PlanId(planId));
        planVersionRepository.deleteAll(planVersionRepository.findByPlan_PlanId(planId));
        confirmerRepository.deleteAll(confirmerRepository.findByPlan_PlanIdOrderByConfirmIdAsc(planId));
        lifeAreaRepository.deleteAll(lifeAreaRepository.findByPlan_PlanId(planId));
        conversationRepository.deleteAll(conversationRepository.findByPlan_PlanId(planId));
        planRepository.delete(plan);
    }

    // S3 원본까지 함께 정리한다. 이미 자동/수동 삭제 절차로 원본이 지워진 증빙(deletedAt != null)은
    // 다시 지우려고 시도하지 않는다 - 존재하지 않는 스토리지 키를 지우는 시도를 막기 위함.
    private void deleteEvidenceWithStorage(Long planId) {
        var evidences = evidenceRepository.findByPlan_PlanId(planId);
        for (Evidence evidence : evidences) {
            if (evidence.getDeletedAt() == null) {
                evidenceStorageClient.delete(evidence.getStorageKey());
            }
        }
        evidenceRepository.deleteAll(evidences);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
