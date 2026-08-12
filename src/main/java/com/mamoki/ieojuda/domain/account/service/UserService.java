package com.mamoki.ieojuda.domain.account.service;

import com.mamoki.ieojuda.domain.account.dto.UserResponse;
import com.mamoki.ieojuda.domain.account.dto.UserUpdateRequest;
import com.mamoki.ieojuda.domain.account.entity.User;
import com.mamoki.ieojuda.domain.account.repository.UserRepository;
import com.mamoki.ieojuda.domain.confirmer.repository.DisputeContactRepository;
import com.mamoki.ieojuda.domain.plan.entity.Plan;
import com.mamoki.ieojuda.domain.plan.repository.ConversationRepository;
import com.mamoki.ieojuda.domain.plan.repository.ItemRepository;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaMessageRepository;
import com.mamoki.ieojuda.domain.plan.repository.LifeAreaRepository;
import com.mamoki.ieojuda.domain.plan.repository.PlanRepository;
import com.mamoki.ieojuda.domain.recipient.repository.RecipientRepository;
import com.mamoki.ieojuda.global.exception.CustomException;
import com.mamoki.ieojuda.global.exception.ErrorCode;
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

    @Transactional
    public UserResponse updateProfile(Long userId, UserUpdateRequest request) {
        User user = findUser(userId);

        if (!user.getEmail().equals(request.email())
                && userRepository.existsByEmailAndUserIdNot(request.email(), userId)) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        user.updateProfile(request.email(), request.name());
        return UserResponse.from(user);
    }

    // 계정·계획 데이터를 영구 삭제한다(되돌릴 수 없음).
    // confirmer(Confirmer)/evidence/releasecase 등은 아직 어떤 서비스도 행을 만들지 않아 삭제 대상이 없다 -
    // 나중에 그 도메인들이 실제로 wiring되면 여기에 삭제 순서를 추가해야 한다.
    @Transactional
    public void deleteAccount(Long userId) {
        User user = findUser(userId);

        planRepository.findByUser_UserId(userId).ifPresent(this::deletePlanData);

        userRepository.delete(user);
    }

    // 자식 -> 부모 순서로 삭제 (FK 제약 위반 방지)
    private void deletePlanData(Plan plan) {
        Long planId = plan.getPlanId();

        lifeAreaMessageRepository.deleteAll(lifeAreaMessageRepository.findByConversation_Plan_PlanId(planId));
        itemRepository.deleteAll(itemRepository.findByLifeArea_Plan_PlanIdOrderByItemIdAsc(planId));
        recipientRepository.deleteAll(recipientRepository.findByPlan_PlanId(planId));
        disputeContactRepository.deleteAll(disputeContactRepository.findByPlan_PlanId(planId));
        lifeAreaRepository.deleteAll(lifeAreaRepository.findByPlan_PlanId(planId));
        conversationRepository.deleteAll(conversationRepository.findByPlan_PlanId(planId));
        planRepository.delete(plan);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
